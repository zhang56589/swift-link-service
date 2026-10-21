package com.swiftlink.dto;

import lombok.Data;

/**
 * 短链接缓存值，notFound 用于空值缓存，防止缓存穿透。
 */
@Data
public class LinkCacheDTO {

    private String shortCode;

    private String originUrl;

    private String businessDomain;

    private Integer status;

    /**
     * 过期时间戳（毫秒），为空表示永不过期
     */
    private Long expireAt;

    private boolean notFound;
}
