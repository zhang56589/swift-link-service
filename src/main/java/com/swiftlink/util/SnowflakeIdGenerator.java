package com.swiftlink.util;

import com.swiftlink.config.SwiftLinkProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 雪花算法：41bit 时间戳 + 5bit 机房 + 5bit 机器 + 12bit 序列号。
 * 单机每毫秒可生成 4096 个 ID，集群下通过 workerId/datacenterId 保证唯一。
 */
@Slf4j
@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1704067200000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long workerId;
    private final long datacenterId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(SwiftLinkProperties properties) {
        long worker = properties.getSnowflake().getWorkerId();
        long datacenter = properties.getSnowflake().getDatacenterId();
        if (worker < 0 || worker > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 必须在 0~31 之间");
        }
        if (datacenter < 0 || datacenter > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId 必须在 0~31 之间");
        }
        this.workerId = worker;
        this.datacenterId = datacenter;
        log.info("雪花算法初始化完成，workerId={}, datacenterId={}", worker, datacenter);
    }

    public synchronized long nextId() {
        long timestamp = currentTime();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > 5) {
                throw new IllegalStateException("系统时钟回拨超过 5ms，拒绝生成 ID");
            }
            timestamp = waitUntil(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitUntil(long target) {
        long timestamp = currentTime();
        while (timestamp <= target) {
            timestamp = currentTime();
        }
        return timestamp;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }
}
