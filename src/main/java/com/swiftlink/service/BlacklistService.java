package com.swiftlink.service;

import com.swiftlink.config.SwiftLinkProperties;
import com.swiftlink.exception.BusinessException;
import com.swiftlink.exception.ErrorCode;
import com.swiftlink.util.RedisKeyUtil;
import com.swiftlink.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 黑名单与域名白名单校验，防止恶意刷量与恶意域名跳转。
 * 黑名单存放在 Redis Set 中，网关或运营后台写入后所有节点即时生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistService {

    private final StringRedisTemplate redisTemplate;

    private final SwiftLinkProperties properties;

    public void assertIpAllowed(String ip) {
        if (ip == null || ip.isBlank()) {
            throw new BusinessException(ErrorCode.BLACKLISTED);
        }
        Boolean blocked = redisTemplate.opsForSet().isMember(RedisKeyUtil.BLACKLIST_IP, ip);
        if (Boolean.TRUE.equals(blocked)) {
            log.warn("命中 IP 黑名单，ip={}", ip);
            throw new BusinessException(ErrorCode.BLACKLISTED);
        }
    }

    /**
     * 创建与跳转两个阶段都校验，防止域名在缓存有效期内被加入黑名单后继续跳转。
     */
    public void assertUrlAllowed(String url) {
        if (!UrlUtil.isHttpUrl(url)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "仅支持 http/https 链接");
        }
        String host = UrlUtil.extractHost(url);
        if (host == null || UrlUtil.isInternalHost(host)) {
            log.warn("拦截内网或非法域名，host={}", host);
            throw new BusinessException(ErrorCode.BLACKLISTED);
        }
        Boolean blocked = redisTemplate.opsForSet().isMember(RedisKeyUtil.BLACKLIST_HOST, host);
        if (Boolean.TRUE.equals(blocked)) {
            log.warn("命中域名黑名单，host={}", host);
            throw new BusinessException(ErrorCode.BLACKLISTED);
        }
        List<String> allowHosts = properties.getAllowHosts();
        if (allowHosts != null && !allowHosts.isEmpty()) {
            boolean allowed = allowHosts.stream()
                    .anyMatch(item -> host.equals(item) || host.endsWith("." + item));
            if (!allowed) {
                log.warn("域名不在白名单内，host={}", host);
                throw new BusinessException(ErrorCode.BLACKLISTED);
            }
        }
    }

    public void addIp(String ip) {
        redisTemplate.opsForSet().add(RedisKeyUtil.BLACKLIST_IP, ip);
    }

    public void removeIp(String ip) {
        redisTemplate.opsForSet().remove(RedisKeyUtil.BLACKLIST_IP, ip);
    }

    public void addHost(String host) {
        redisTemplate.opsForSet().add(RedisKeyUtil.BLACKLIST_HOST, host.toLowerCase());
    }

    public void removeHost(String host) {
        redisTemplate.opsForSet().remove(RedisKeyUtil.BLACKLIST_HOST, host.toLowerCase());
    }
}
