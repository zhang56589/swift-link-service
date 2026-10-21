package com.swiftlink.controller;

import com.swiftlink.config.SwiftLinkProperties;
import com.swiftlink.dto.BlacklistRequest;
import com.swiftlink.dto.Result;
import com.swiftlink.exception.BusinessException;
import com.swiftlink.exception.ErrorCode;
import com.swiftlink.service.BlacklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部黑名单管理接口，生产环境应由网关鉴权或内网访问控制兜底。
 */
@RestController
@RequestMapping("/api/admin/blacklist")
@RequiredArgsConstructor
public class BlacklistController {

    private final BlacklistService blacklistService;

    private final SwiftLinkProperties properties;

    @PostMapping("/ip")
    public Result<Void> addIp(@RequestHeader("X-Admin-Token") String token,
                              @Valid @RequestBody BlacklistRequest request) {
        authenticate(token);
        blacklistService.addIp(request.getValue());
        return Result.ok();
    }

    @DeleteMapping("/ip")
    public Result<Void> removeIp(@RequestHeader("X-Admin-Token") String token,
                                 @RequestParam String value) {
        authenticate(token);
        blacklistService.removeIp(value);
        return Result.ok();
    }

    @PostMapping("/host")
    public Result<Void> addHost(@RequestHeader("X-Admin-Token") String token,
                                @Valid @RequestBody BlacklistRequest request) {
        authenticate(token);
        blacklistService.addHost(request.getValue());
        return Result.ok();
    }

    @DeleteMapping("/host")
    public Result<Void> removeHost(@RequestHeader("X-Admin-Token") String token,
                                   @RequestParam String value) {
        authenticate(token);
        blacklistService.removeHost(value);
        return Result.ok();
    }

    /**
     * 常量时间比较，避免令牌校验被时序攻击逐字符猜测。
     */
    private void authenticate(String token) {
        byte[] actual = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        byte[] expected = properties.getAdminToken().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(actual, expected)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
