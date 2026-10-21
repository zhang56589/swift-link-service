package com.swiftlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_link_daily_stats")
public class LinkDailyStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shortCode;

    private LocalDate statDate;

    private Long pv;

    private Long uv;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
