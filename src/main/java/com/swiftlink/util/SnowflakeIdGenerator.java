package com.swiftlink.util;

import com.swiftlink.config.SwiftLinkProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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

    private static final int MAX_WAIT_ATTEMPTS = 1000;

    private static final long MAX_WAIT_MILLIS = 500L;

    private final long workerId;
    private final long datacenterId;

    // [优化点] 限时锁替代 synchronized，拿不到锁快速失败，避免线程无限阻塞
    private final ReentrantLock lock = new ReentrantLock();

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

    public long nextId() {
        boolean locked = false;
        try {
            // [优化点] 200ms 限时抢锁，超时快速失败，防止 synchronized 无限阻塞堆积线程
            locked = lock.tryLock(200, TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new IllegalStateException("获取发号锁超时，系统繁忙");
            }
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取发号锁被中断，系统繁忙", e);
        } finally {
            // [优化点] 仅持有锁的线程才解锁，防止未抢到锁的线程误解锁报错
            if (locked) {
                lock.unlock();
            }
        }
    }

    private long waitUntil(long target) {
        // [优化点] 限制最大等待次数与超时时间，防止系统时钟停滞导致 while 死循环耗尽 CPU
        int attempts = 0;
        long deadline = currentTime() + MAX_WAIT_MILLIS;
        long timestamp = currentTime();
        while (timestamp <= target) {
            if (++attempts > MAX_WAIT_ATTEMPTS || currentTime() > deadline) {
                throw new IllegalStateException("等待下一毫秒超时，系统时钟可能停滞");
            }
            Thread.yield();
            timestamp = currentTime();
        }
        return timestamp;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }
}
