package com.swiftlink.service;

import com.swiftlink.config.SwiftLinkProperties;
import com.swiftlink.dto.AccessLogMessage;
import com.swiftlink.dto.LinkCacheDTO;
import com.swiftlink.entity.ShortLink;
import com.swiftlink.exception.BusinessException;
import com.swiftlink.exception.ErrorCode;
import com.swiftlink.mapper.ShortLinkMapper;
import com.swiftlink.util.LinkStatus;
import com.swiftlink.util.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * 跳转核心链路：限流 -> 黑名单 -> 缓存 -> 回源 -> 异步统计。
 * 缓存未命中时使用 Redisson 锁重建，避免热点 key 失效瞬间击穿数据库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {

    private final RateLimitService rateLimitService;

    private final BlacklistService blacklistService;

    private final LinkCacheService linkCacheService;

    private final ShortLinkMapper shortLinkMapper;

    private final AccessLogService accessLogService;

    private final RedissonClient redissonClient;

    private final SwiftLinkProperties properties;

    public String resolve(String shortCode, String clientIp, String userAgent, String referer) {
        rateLimitService.checkIp(clientIp);
        blacklistService.assertIpAllowed(clientIp);
        rateLimitService.checkCode(shortCode);

        LinkCacheDTO cache = linkCacheService.get(shortCode);
        if (cache == null) {
            cache = loadWithRebuildLock(shortCode);
        }
        if (cache.isNotFound() || cache.getOriginUrl() == null) {
            throw new BusinessException(ErrorCode.LINK_NOT_FOUND);
        }
        if (cache.getStatus() == null || cache.getStatus() != LinkStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.LINK_DISABLED);
        }
        if (cache.getExpireAt() != null && cache.getExpireAt() <= System.currentTimeMillis()) {
            throw new BusinessException(ErrorCode.LINK_EXPIRED);
        }
        blacklistService.assertUrlAllowed(cache.getOriginUrl());

        AccessLogMessage message = new AccessLogMessage();
        message.setShortCode(shortCode);
        message.setClientIp(clientIp);
        message.setUserAgent(userAgent);
        message.setReferer(referer);
        message.setAccessTime(LocalDateTime.now());
        accessLogService.recordAsync(message);
        return cache.getOriginUrl();
    }

    private LinkCacheDTO loadWithRebuildLock(String code) {
        RLock lock = redissonClient.getLock(RedisKeyUtil.cacheRebuildLock(code));
        boolean locked = false;
        try {
            locked = lock.tryLock(50, 1000, TimeUnit.MILLISECONDS);
            if (locked) {
                LinkCacheDTO doubleCheck = linkCacheService.get(code);
                if (doubleCheck != null) {
                    return doubleCheck;
                }
                return loadFromDatabase(code);
            }
            // 未抢到锁时短暂自旋等待缓存重建，避免并发请求同时击穿数据库
            for (int i = 0; i < 3; i++) {
                Thread.sleep(20);
                LinkCacheDTO cache = linkCacheService.get(code);
                if (cache != null) {
                    return cache;
                }
            }
            // [优化点] 自旋 60ms 仍未命中，说明重建异常或极慢，直接降级，不再回源压向数据库
            log.warn("Cache rebuild timeout after spin wait, degrade to SYSTEM_BUSY, code={}", code);
            throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loadFromDatabase(code);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private LinkCacheDTO loadFromDatabase(String code) {
        ShortLink link;
        try {
            link = shortLinkMapper.selectByShortCode(code);
        } catch (Exception e) {
            // [优化点] 数据库宕机或超时，直接降级，防止后续请求继续压向数据库
            log.error("Load short link from database failed, degrade to SYSTEM_BUSY, code={}", code, e);
            throw new BusinessException(ErrorCode.SYSTEM_BUSY);
        }
        if (link == null) {
            linkCacheService.putNull(code);
            LinkCacheDTO dto = new LinkCacheDTO();
            dto.setShortCode(code);
            dto.setNotFound(true);
            return dto;
        }

        LinkCacheDTO dto = new LinkCacheDTO();
        dto.setShortCode(link.getShortCode());
        dto.setOriginUrl(link.getOriginUrl());
        dto.setBusinessDomain(link.getBusinessDomain());
        dto.setStatus(link.getStatus());
        if (link.getExpireTime() != null) {
            dto.setExpireAt(link.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }

        long ttl = properties.getCache().getTtlSeconds();
        if (dto.getExpireAt() != null) {
            ttl = Math.min(ttl, (dto.getExpireAt() - System.currentTimeMillis()) / 1000);
        }
        if (ttl > 0) {
            linkCacheService.put(dto, ttl);
        }
        return dto;
    }
}
