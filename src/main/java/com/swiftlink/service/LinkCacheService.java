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
            LinkCacheDTO dto = objectMapper.readValue(value, LinkCacheDTO.class);
            // [优化点] 反序列化结果为 null 同样视为脏数据，统一走下方异常捕获，避免后续调用 NPE
            if (dto == null) {
                throw new JsonProcessingException("deserialized dto is null") {};
            }
            return dto;
        } catch (JsonProcessingException e) {
            // [优化点] 脏数据改写为空值缓存而非直接删除，防止脏数据被反复写入后持续击穿数据库
            log.warn("缓存反序列化失败，改写空值缓存防止脏数据反复击穿数据库，code={}", code);
            putNull(code);
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
