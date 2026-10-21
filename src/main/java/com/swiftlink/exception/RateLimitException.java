package com.swiftlink.exception;

import lombok.Getter;

/**
 * 限流异常携带重试时间，响应头 Retry-After 可直接用于客户端退避。
 */
@Getter
public class RateLimitException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
