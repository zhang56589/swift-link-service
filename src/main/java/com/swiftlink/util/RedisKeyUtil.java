package com.swiftlink.util;

/**
 * Redis key 统一管理，避免散落的字符串拼接导致维度错乱。
 */
public final class RedisKeyUtil {

    public static final String SHORT_LINK_CACHE_PREFIX = "short:link:";

    public static final String CACHE_REBUILD_LOCK_PREFIX = "lock:cache:rebuild:";

    public static final String SHORT_CODE_LOCK_PREFIX = "lock:short:code:";

    public static final String POOL_REPLENISH_LOCK = "lock:short:pool:replenish";

    public static final String RATE_LIMIT_IP_PREFIX = "rl:ip:";

    public static final String RATE_LIMIT_CODE_PREFIX = "rl:code:";

    public static final String BLACKLIST_IP = "short:blacklist:ip";

    public static final String BLACKLIST_HOST = "short:blacklist:host";

    public static final String STATS_PV_PREFIX = "short:stats:pv:";

    public static final String STATS_UV_PREFIX = "short:stats:uv:";

    public static final String STATS_ACTIVE = "short:stats:active";

    public static final String STATS_FLUSH_LOCK = "lock:stats:flush";

    private RedisKeyUtil() {
    }

    public static String linkCache(String code) {
        return SHORT_LINK_CACHE_PREFIX + code;
    }

    public static String cacheRebuildLock(String code) {
        return CACHE_REBUILD_LOCK_PREFIX + code;
    }

    public static String shortCodeLock(String code) {
        return SHORT_CODE_LOCK_PREFIX + code;
    }

    public static String rateLimitIp(String ip, long windowIndex) {
        return RATE_LIMIT_IP_PREFIX + ip + ":" + windowIndex;
    }

    public static String rateLimitCode(String code, long windowIndex) {
        return RATE_LIMIT_CODE_PREFIX + code + ":" + windowIndex;
    }

    public static String statsPvKey(String code, String date) {
        return STATS_PV_PREFIX + code + ":" + date;
    }

    public static String statsUvKey(String code, String date) {
        return STATS_UV_PREFIX + code + ":" + date;
    }
}
