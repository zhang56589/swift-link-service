package com.swiftlink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.swiftlink.dto.LinkStatsVO;
import com.swiftlink.entity.LinkDailyStats;
import com.swiftlink.entity.ShortLink;
import com.swiftlink.exception.BusinessException;
import com.swiftlink.exception.ErrorCode;
import com.swiftlink.mapper.LinkDailyStatsMapper;
import com.swiftlink.mapper.ShortLinkMapper;
import com.swiftlink.util.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 访问统计：实时数据读 Redis，历史数据读 MySQL，定时任务将当日计数落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;

    private final LinkDailyStatsMapper dailyStatsMapper;

    private final ShortLinkMapper shortLinkMapper;

    public LinkStatsVO query(String shortCode, LocalDate date) {
        ShortLink link = shortLinkMapper.selectByShortCode(shortCode);
        if (link == null) {
            throw new BusinessException(ErrorCode.LINK_NOT_FOUND);
        }
        LocalDate target = date == null ? LocalDate.now() : date;
        String dateStr = target.format(DATE_FORMATTER);
        long pv;
        long uv;
        if (target.equals(LocalDate.now())) {
            pv = readPv(RedisKeyUtil.statsPvKey(shortCode, dateStr));
            uv = readUv(RedisKeyUtil.statsUvKey(shortCode, dateStr));
        } else {
            LinkDailyStats row = dailyStatsMapper.selectOne(new LambdaQueryWrapper<LinkDailyStats>()
                    .eq(LinkDailyStats::getShortCode, shortCode)
                    .eq(LinkDailyStats::getStatDate, target));
            pv = row == null ? 0 : nvl(row.getPv());
            uv = row == null ? 0 : nvl(row.getUv());
        }

        LinkDailyStats total = dailyStatsMapper.selectTotalByCode(shortCode);
        LinkStatsVO vo = new LinkStatsVO();
        vo.setShortCode(shortCode);
        vo.setDate(target);
        vo.setPv(pv);
        vo.setUv(uv);
        vo.setTotalPv(total == null ? 0 : nvl(total.getPv()));
        vo.setTotalUv(total == null ? 0 : nvl(total.getUv()));
        return vo;
    }

    /**
     * 每 5 分钟把活跃统计 key 落入 MySQL；重复执行时由 GREATEST 保证数据不回退。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void flushToDatabase() {
        Set<String> keys = redisTemplate.opsForSet().members(RedisKeyUtil.STATS_ACTIVE);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String pvKey : keys) {
            try {
                flushOne(pvKey);
            } catch (Exception e) {
                log.warn("统计落库失败，key={}: {}", pvKey, e.getMessage());
            }
        }
    }

    private void flushOne(String pvKey) {
        String prefix = RedisKeyUtil.STATS_PV_PREFIX;
        if (!pvKey.startsWith(prefix)) {
            redisTemplate.opsForSet().remove(RedisKeyUtil.STATS_ACTIVE, pvKey);
            return;
        }
        String suffix = pvKey.substring(prefix.length());
        int split = suffix.lastIndexOf(':');
        if (split <= 0) {
            redisTemplate.opsForSet().remove(RedisKeyUtil.STATS_ACTIVE, pvKey);
            return;
        }
        String code = suffix.substring(0, split);
        String dateStr = suffix.substring(split + 1);

        Object pvValue = redisTemplate.opsForHash().get(pvKey, "pv");
        if (pvValue == null) {
            redisTemplate.opsForSet().remove(RedisKeyUtil.STATS_ACTIVE, pvKey);
            return;
        }
        LocalDate statDate;
        try {
            statDate = LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (Exception e) {
            redisTemplate.opsForSet().remove(RedisKeyUtil.STATS_ACTIVE, pvKey);
            return;
        }
        long pv = Long.parseLong(String.valueOf(pvValue));
        long uv = readUv(RedisKeyUtil.statsUvKey(code, dateStr));

        LinkDailyStats stats = new LinkDailyStats();
        stats.setShortCode(code);
        stats.setStatDate(statDate);
        stats.setPv(pv);
        stats.setUv(uv);
        dailyStatsMapper.upsert(stats);
    }

    private long readPv(String pvKey) {
        Object value = redisTemplate.opsForHash().get(pvKey, "pv");
        return value == null ? 0 : Long.parseLong(String.valueOf(value));
    }

    private long readUv(String uvKey) {
        Long size = redisTemplate.opsForHyperLogLog().size(uvKey);
        return size == null ? 0 : size;
    }

    private long nvl(Long value) {
        return value == null ? 0 : value;
    }
}
