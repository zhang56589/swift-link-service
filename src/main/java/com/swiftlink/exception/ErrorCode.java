package com.swiftlink.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一错误码，业务码与 HTTP 状态码分离，便于网关和前端分别处理。
 */
@Getter
public enum ErrorCode {

    INVALID_PARAM(40000, "参数不合法", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "无权访问", HttpStatus.UNAUTHORIZED),
    LINK_NOT_FOUND(40401, "短链接不存在", HttpStatus.NOT_FOUND),
    LINK_DISABLED(40301, "短链接已被停用", HttpStatus.FORBIDDEN),
    BLACKLISTED(40302, "请求已被安全策略拦截", HttpStatus.FORBIDDEN),
    LINK_EXPIRED(41001, "短链接已过期", HttpStatus.GONE),
    RATE_LIMITED(42901, "访问过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    SYSTEM_ERROR(50000, "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),
    SYSTEM_BUSY(50300, "系统繁忙，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;

    private final String message;

    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
