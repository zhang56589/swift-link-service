package com.swiftlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_short_link")
public class ShortLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shortCode;

    private String originUrl;

    private String businessDomain;

    private LocalDateTime expireTime;

    /**
     * 0-停用，1-启用
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
