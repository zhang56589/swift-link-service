package com.swiftlink.service;

import com.swiftlink.config.SwiftLinkProperties;
import com.swiftlink.exception.RateLimitException;
import com.swiftlink.util.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 基于 Redis + Lua 的固定窗口限流。
 * 计数与过期在同一个 Lua 脚本中完成，保证原子性；窗口序号拼入 key，避免边界清理问题。
 * Redis 异常时降级放行，优先保障跳转可用性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<List> rateLimitScript;

    private final SwiftLinkProperties properties;

    public void checkIp(String ip) {
        SwiftLinkProperties.RateLimit config = properties.getRateLimit();
        long windowSeconds = Math.max(1, config.getIpWindowSeconds());
        long windowIndex = System.currentTimeMillis() / 1000 / windowSeconds;
        try {
            check(RedisKeyUtil.rateLimitIp(ip, windowIndex), config.getIpLimit(), windowSeconds * 1000);
        } catch (RateLimitException e) {
            // [优化点] 限流拦截时打印被限制的 IP，便于监控防刷
            log.warn("触发限流拦截，维度=ip，ip={}, retryAfter={}s", ip, e.getRetryAfterSeconds());
            throw e;
        }
    }

    public void checkCode(String code) {
        SwiftLinkProperties.RateLimit config = properties.getRateLimit();
        long windowSeconds = Math.max(1, config.getCodeWindowSeconds());
        long windowIndex = System.currentTimeMillis() / 1000 / windowSeconds;
        try {
            check(RedisKeyUtil.rateLimitCode(code, windowIndex), config.getCodeLimit(), windowSeconds * 1000);
        } catch (RateLimitException e) {
            // [优化点] 限流拦截时打印被限制的短码，便于监控防刷
            log.warn("触发限流拦截，维度=code，code={}, retryAfter={}s", code, e.getRetryAfterSeconds());
            throw e;
        }
    }

    private void check(String key, long limit, long windowMillis) {
        try {
            List<?> result = redisTemplate.execute(rateLimitScript, Collections.singletonList(key),
                    String.valueOf(limit), String.valueOf(windowMillis));
            if (result == null || result.isEmpty()) {
                return;
            }
            long allowed = ((Number) result.get(0)).longValue();
            if (allowed == 0L) {
                long ttl = result.size() > 2 ? ((Number) result.get(2)).longValue() : windowMillis;
                long retryAfterSeconds = Math.max(1, ttl / 1000);
                throw new RateLimitException(retryAfterSeconds);
            }
        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            // [优化点] 限流组件（Redis）异常必须降级放行并记录 error，绝不能让限流挂掉带崩跳转链路
            log.error("限流组件异常，降级放行，key={}", key, e);
        }
    }
}
