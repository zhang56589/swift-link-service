package com.swiftlink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 短链接服务业务配置，生产环境可通过 Nacos 配置中心动态下发。
 */
@Data
@Component
@ConfigurationProperties(prefix = "swift-link")
public class SwiftLinkProperties {

    private String domain = "http://s.link";
    private String adminToken = "change-me";
    private List<String> allowHosts = new ArrayList<>();
    private Cache cache = new Cache();
    private CodePool codePool = new CodePool();
    private RateLimit rateLimit = new RateLimit();
    private Snowflake snowflake = new Snowflake();

    @Data
    public static class Cache {
        private long ttlSeconds = 1800;
        private long nullTtlSeconds = 30;
        private long jitterSeconds = 300;
    }

    @Data
    public static class CodePool {
        private String key = "short:code:pool";
        private int minSize = 500;
        private int batchSize = 2000;
        private int maxSize = 20000;
        private long replenishLockSeconds = 10;
    }

    @Data
    public static class RateLimit {
        private long ipLimit = 60;
        private long ipWindowSeconds = 60;
        private long codeLimit = 1000;
        private long codeWindowSeconds = 60;
    }

    @Data
    public static class Snowflake {
        private long workerId = 1;
        private long datacenterId = 1;
    }
}
