package com.swiftlink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.swiftlink.config.SwiftLinkProperties;
import com.swiftlink.dto.CreateLinkRequest;
import com.swiftlink.dto.CreateLinkResponse;
import com.swiftlink.dto.LinkDetailVO;
import com.swiftlink.entity.ShortLink;
import com.swiftlink.exception.BusinessException;
import com.swiftlink.exception.ErrorCode;
import com.swiftlink.mapper.ShortLinkMapper;
import com.swiftlink.util.LinkStatus;
import com.swiftlink.util.RedisKeyUtil;
import com.swiftlink.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkService {

    private static final int MAX_ATTEMPTS = 3;

    private final ShortLinkMapper shortLinkMapper;

    private final ShortCodeService shortCodeService;

    private final LinkCacheService linkCacheService;

    private final BlacklistService blacklistService;

    private final SwiftLinkProperties properties;

    private final RedissonClient redissonClient;

    /**
     * 创建短链接：短码来自 Redis 池，Redisson 锁 + 唯一索引双重保证全局唯一。
     * 这里不加数据库事务，确保 insert 自动提交后再释放锁。
     */
    public CreateLinkResponse create(CreateLinkRequest request) {
        String originUrl = request.getOriginUrl() == null ? null : request.getOriginUrl().trim();
        if (!UrlUtil.isHttpUrl(originUrl)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "仅支持合法的 http/https 链接");
        }
        if (request.getExpireTime() != null && !request.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "过期时间必须晚于当前时间");
        }
        blacklistService.assertUrlAllowed(originUrl);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = shortCodeService.acquire();
            RLock lock = redissonClient.getLock(RedisKeyUtil.shortCodeLock(code));
            boolean locked = false;
            try {
                locked = lock.tryLock(1, 3, TimeUnit.SECONDS);
                if (!locked) {
                    // [优化点] 未抢到锁说明短码本次未使用，归还池子避免浪费
                    log.warn("短码锁获取失败，归还短码并尝试下一个，code={}", code);
                    shortCodeService.release(code);
                    continue;
                }
                Long exists = shortLinkMapper.selectCount(new LambdaQueryWrapper<ShortLink>()
                        .eq(ShortLink::getShortCode, code));
                if (exists != null && exists > 0) {
                    // [优化点] 短码已存在（雪花重复概率极低），归还池子避免浪费
                    log.warn("短码已存在，归还短码并重新获取，code={}", code);
                    shortCodeService.release(code);
                    continue;
                }
                LocalDateTime now = LocalDateTime.now();
                ShortLink link = new ShortLink();
                link.setShortCode(code);
                link.setOriginUrl(originUrl);
                link.setBusinessDomain(request.getBusinessDomain());
                link.setExpireTime(request.getExpireTime());
                link.setStatus(LinkStatus.ACTIVE);
                link.setCreateTime(now);
                link.setUpdateTime(now);
                shortLinkMapper.insert(link);
                log.info("短链接创建成功，code={}, originUrl={}", code, originUrl);
                return toCreateResponse(link);
            } catch (DuplicateKeyException e) {
                log.warn("短码唯一索引冲突，重新生成，code={}", code);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建短链接被中断");
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "短码生成冲突，请稍后重试");
    }

    public LinkDetailVO detail(String code) {
        ShortLink link = shortLinkMapper.selectByShortCode(code);
        if (link == null) {
            throw new BusinessException(ErrorCode.LINK_NOT_FOUND);
        }
        LinkDetailVO vo = new LinkDetailVO();
        vo.setShortCode(link.getShortCode());
        vo.setShortUrl(buildShortUrl(link.getShortCode()));
        vo.setOriginUrl(link.getOriginUrl());
        vo.setBusinessDomain(link.getBusinessDomain());
        vo.setStatus(link.getStatus());
        vo.setStatusDesc(LinkStatus.desc(link.getStatus()));
        vo.setExpireTime(link.getExpireTime());
        vo.setCreateTime(link.getCreateTime());
        return vo;
    }

    /**
     * 状态变更后立即删除缓存，集群节点下次访问回源加载最新状态。
     */
    public void updateStatus(String code, Integer status) {
        if (!LinkStatus.isValid(status)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "状态只能是 0 或 1");
        }
        ShortLink link = shortLinkMapper.selectByShortCode(code);
        if (link == null) {
            throw new BusinessException(ErrorCode.LINK_NOT_FOUND);
        }
        if (status.equals(link.getStatus())) {
            return;
        }
        ShortLink update = new ShortLink();
        update.setId(link.getId());
        update.setStatus(status);
        shortLinkMapper.updateById(update);
        linkCacheService.evict(code);
        log.info("短链接状态更新，code={}, status={}", code, status);
    }

    private CreateLinkResponse toCreateResponse(ShortLink link) {
        CreateLinkResponse response = new CreateLinkResponse();
        response.setShortCode(link.getShortCode());
        response.setShortUrl(buildShortUrl(link.getShortCode()));
        response.setExpireTime(link.getExpireTime());
        response.setCreateTime(link.getCreateTime());
        return response;
    }

    private String buildShortUrl(String code) {
        String domain = properties.getDomain() == null ? "" : properties.getDomain().trim();
        while (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain + "/" + code;
    }
}
