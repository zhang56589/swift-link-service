package com.swiftlink.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析，兼容 Nginx / 网关转发场景。
 */
public final class IpUtil {

    private static final String UNKNOWN = "unknown";

    private IpUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isValid(ip)) {
            int comma = ip.indexOf(',');
            if (comma > 0) {
                ip = ip.substring(0, comma);
            }
            return normalize(ip);
        }
        ip = request.getHeader("X-Real-IP");
        if (isValid(ip)) {
            return normalize(ip);
        }
        return normalize(request.getRemoteAddr());
    }

    private static boolean isValid(String ip) {
        return ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip);
    }

    private static String normalize(String ip) {
        String value = ip == null ? UNKNOWN : ip.trim();
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
}
