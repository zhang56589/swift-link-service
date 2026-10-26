package com.swiftlink.service;

import com.swiftlink.config.SwiftLinkProperties;
import com.swiftlink.util.Base62Util;
import com.swiftlink.util.RedisKeyUtil;
import com.swiftlink.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 短码生成：雪花 ID 保证唯一，Base62 缩短长度，Redis List 预生成池把生成成本前置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortCodeService {

    private final StringRedisTemplate redisTemplate;

    private final RedissonClient redissonClient;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final SwiftLinkProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            replenishIfNeeded();
        } catch (Exception e) {
            log.warn("短码池预热失败，将在首次创建时重试: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 30000L)
    public void scheduledReplenish() {
        try {
            replenishIfNeeded();
        } catch (Exception e) {
            log.warn("定时补充短码池失败: {}", e.getMessage());
        }
    }

    /**
     * 优先从池中弹出，池空时在分布式锁内补充，仍为空则直接生成兜底，保证创建接口不被池容量阻塞。
     */
    public String acquire() {
        String poolKey = properties.getCodePool().getKey();
        String code = redisTemplate.opsForList().leftPop(poolKey);
        if (code != null) {
            return code;
        }
        replenishIfNeeded();
        code = redisTemplate.opsForList().leftPop(poolKey);
        if (code != null) {
            return code;
        }
        return Base62Util.encode(snowflakeIdGenerator.nextId());
    }

    /**
     * 归还未成功使用的短码：RPUSH 放回池尾。
     * RPUSH 本身原子，无需再加分布式锁，避免与补充锁嵌套引发死锁；归还失败仅记日志丢弃，不阻塞创建链路。
     */
    public void release(String code) {
        if (code == null || code.isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForList().rightPush(properties.getCodePool().getKey(), code);
        } catch (Exception e) {
            log.warn("短码归还失败，直接丢弃，code={}: {}", code, e.getMessage());
        }
    }

    /**
     * 池容量低于阈值时补充；Redisson 锁保证集群中只有一个节点执行批量生成。
     */
    public void replenishIfNeeded() {
        String poolKey = properties.getCodePool().getKey();
        Long size = redisTemplate.opsForList().size(poolKey);
        if (size != null && size >= properties.getCodePool().getMinSize()) {
            return;
        }
        RLock lock = redissonClient.getLock(RedisKeyUtil.POOL_REPLENISH_LOCK);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, properties.getCodePool().getReplenishLockSeconds(), TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            size = redisTemplate.opsForList().size(poolKey);
            if (size != null && size >= properties.getCodePool().getMinSize()) {
                return;
            }
            int current = size == null ? 0 : size.intValue();
            int need = Math.min(properties.getCodePool().getBatchSize(),
                    properties.getCodePool().getMaxSize() - current);
            if (need <= 0) {
                return;
            }
            List<String> codes = new ArrayList<>(need);
            for (int i = 0; i < need; i++) {
                codes.add(Base62Util.encode(snowflakeIdGenerator.nextId()));
            }
            redisTemplate.opsForList().rightPushAll(poolKey, codes);
            log.info("短码池补充完成，本次新增 {} 个，当前约 {} 个", need, current + need);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
