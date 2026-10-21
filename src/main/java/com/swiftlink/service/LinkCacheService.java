package com.swiftlink.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftlink.config.SwiftLinkProperties;
import com.swiftlink.dto.LinkCacheDTO;
import com.swiftlink.util.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 短链接缓存：TTL 加随机抖动防止雪崩，空值缓存防止穿透。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkCacheService {

    private static final String NULL_VALUE = "__NULL__";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final SwiftLinkProperties properties;

    public LinkCacheDTO get(String code) {
        String key = RedisKeyUtil.linkCache(code);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        if (NULL_VALUE.equals(value)) {
            LinkCacheDTO dto = new LinkCacheDTO();
            dto.setShortCode(code);
            dto.setNotFound(true);
            return dto;
        }
        try {
            return objectMapper.readValue(value, LinkCacheDTO.class);
        } catch (JsonProcessingException e) {
            log.warn("缓存反序列化失败，删除脏缓存并回源，code={}", code);
            redisTemplate.delete(key);
            return null;
        }
    }

    public void put(LinkCacheDTO dto, long ttlSeconds) {
        long jitter = properties.getCache().getJitterSeconds();
        long ttl = ttlSeconds + (jitter > 0 ? ThreadLocalRandom.current().nextLong(jitter + 1) : 0);
        if (dto.getExpireAt() != null) {
            long remaining = (dto.getExpireAt() - System.currentTimeMillis()) / 1000;
            ttl = Math.min(ttl, remaining);
        }
        if (ttl <= 0) {
            return;
        }
        try {
            String value = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(RedisKeyUtil.linkCache(dto.getShortCode()), value, ttl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("缓存序列化失败，code={}", dto.getShortCode());
        }
    }

    public void putNull(String code) {
        redisTemplate.opsForValue().set(RedisKeyUtil.linkCache(code), NULL_VALUE,
                properties.getCache().getNullTtlSeconds(), TimeUnit.SECONDS);
    }

    public void evict(String code) {
        redisTemplate.delete(RedisKeyUtil.linkCache(code));
    }
}
