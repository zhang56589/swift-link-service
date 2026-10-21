package com.swiftlink.service;

import com.swiftlink.dto.AccessLogMessage;
import com.swiftlink.entity.AccessLog;
import com.swiftlink.mapper.AccessLogMapper;
import com.swiftlink.util.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 访问日志异步落库，同时累加 Redis 中的 PV/UV 计数。
 * 跳转链路只负责投递消息，不受数据库写入耗时影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessLogService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final long STATS_TTL_DAYS = 3;

    private final AccessLogMapper accessLogMapper;

    private final StringRedisTemplate redisTemplate;

    @Async("accessLogExecutor")
    public void recordAsync(AccessLogMessage message) {
        try {
            AccessLog accessLog = new AccessLog();
            accessLog.setShortCode(message.getShortCode());
            accessLog.setClientIp(truncate(message.getClientIp(), 64));
            accessLog.setUserAgent(truncate(message.getUserAgent(), 512));
            accessLog.setReferer(truncate(message.getReferer(), 1024));
            accessLog.setAccessTime(message.getAccessTime());
            accessLogMapper.insert(accessLog);
        } catch (Exception e) {
            log.error("访问日志落库失败，code={}", message.getShortCode(), e);
        }
        try {
            accumulateStats(message);
        } catch (Exception e) {
            log.warn("访问统计累加失败，code={}: {}", message.getShortCode(), e.getMessage());
        }
    }

    /**
     * PV 使用 Hash 计数，UV 使用 HyperLogLog 估算，内存占用固定且天然去重。
     */
    private void accumulateStats(AccessLogMessage message) {
        String date = message.getAccessTime().format(DATE_FORMATTER);
        String pvKey = RedisKeyUtil.statsPvKey(message.getShortCode(), date);
        String uvKey = RedisKeyUtil.statsUvKey(message.getShortCode(), date);
        String ip = message.getClientIp() == null ? "unknown" : message.getClientIp();

        redisTemplate.opsForHash().increment(pvKey, "pv", 1);
        redisTemplate.expire(pvKey, STATS_TTL_DAYS, TimeUnit.DAYS);
        redisTemplate.opsForHyperLogLog().add(uvKey, ip);
        redisTemplate.expire(uvKey, STATS_TTL_DAYS, TimeUnit.DAYS);
        redisTemplate.opsForSet().add(RedisKeyUtil.STATS_ACTIVE, pvKey);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
