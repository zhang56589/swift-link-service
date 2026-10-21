package com.swiftlink.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 原始链接校验：只允许 http/https，禁止 userInfo、内网地址与非法端口写法。
 */
public final class UrlUtil {

    private static final int MAX_LENGTH = 2048;

    private UrlUtil() {
    }

    public static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return false;
            }
            if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
                return false;
            }
            return uri.getPort() <= 65535;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String extractHost(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String host = new URI(url.trim()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * 拦截回环、私有网段、链路本地地址，避免短链服务被用作内网探测入口。
     */
    public static boolean isInternalHost(String host) {
        if (host == null) {
            return true;
        }
        String value = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(value) || "::1".equals(value) || value.endsWith(".localhost")) {
            return true;
        }
        if (value.matches("^127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            return true;
        }
        if (value.matches("^10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            return true;
        }
        if (value.matches("^192\\.168\\.\\d{1,3}\\.\\d{1,3}$")) {
            return true;
        }
        if (value.matches("^169\\.254\\.\\d{1,3}\\.\\d{1,3}$")) {
            return true;
        }
        return value.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\.\\d{1,3}\\.\\d{1,3}$");
    }
}
