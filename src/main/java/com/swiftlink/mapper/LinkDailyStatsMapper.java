package com.swiftlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.swiftlink.entity.LinkDailyStats;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LinkDailyStatsMapper extends BaseMapper<LinkDailyStats> {

    /**
     * 幂等写入：重复落库时只允许 pv/uv 变大，避免定时任务重复执行导致数据回退。
     */
    @Insert("INSERT INTO t_link_daily_stats (short_code, stat_date, pv, uv, create_time, update_time) "
            + "VALUES (#{shortCode}, #{statDate}, #{pv}, #{uv}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE pv = GREATEST(pv, VALUES(pv)), uv = GREATEST(uv, VALUES(uv)), update_time = NOW()")
    int upsert(LinkDailyStats stats);

    @Select("SELECT COALESCE(SUM(pv), 0) AS pv, COALESCE(SUM(uv), 0) AS uv "
            + "FROM t_link_daily_stats WHERE short_code = #{shortCode}")
    LinkDailyStats selectTotalByCode(@Param("shortCode") String shortCode);
}
