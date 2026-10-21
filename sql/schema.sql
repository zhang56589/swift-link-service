-- 分布式短链接服务建表脚本
CREATE DATABASE IF NOT EXISTS `swift_link`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `swift_link`;

-- 短链接主表：id 仅内部使用，对外只暴露 Base62 短码
CREATE TABLE IF NOT EXISTS `t_short_link`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，仅内部使用',
    `short_code`      VARCHAR(16)     NOT NULL COMMENT 'Base62 短码',
    `origin_url`      VARCHAR(2048)   NOT NULL COMMENT '原始长链接',
    `business_domain` VARCHAR(128)             DEFAULT NULL COMMENT '业务标识，用于分组统计',
    `expire_time`     DATETIME                 DEFAULT NULL COMMENT '过期时间，NULL 表示永不过期',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_short_code` (`short_code`),
    KEY `idx_expire_time` (`expire_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='短链接主表';

-- 访问日志表：异步写入，用于明细追溯
CREATE TABLE IF NOT EXISTS `t_access_log`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `short_code`  VARCHAR(16)     NOT NULL COMMENT '短码',
    `client_ip`   VARCHAR(64)              DEFAULT NULL COMMENT '客户端 IP',
    `user_agent`  VARCHAR(512)             DEFAULT NULL COMMENT 'User-Agent',
    `referer`     VARCHAR(1024)            DEFAULT NULL COMMENT 'Referer',
    `access_time` DATETIME(3)     NOT NULL COMMENT '访问时间',
    PRIMARY KEY (`id`),
    KEY `idx_code_time` (`short_code`, `access_time`),
    KEY `idx_access_time` (`access_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='访问日志明细表';

-- 天级统计表：Redis 计数定时落库，pv/uv 采用 GREATEST 幂等更新
CREATE TABLE IF NOT EXISTS `t_link_daily_stats`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `short_code`  VARCHAR(16)     NOT NULL COMMENT '短码',
    `stat_date`   DATE            NOT NULL COMMENT '统计日期',
    `pv`          BIGINT          NOT NULL DEFAULT 0 COMMENT '页面浏览量',
    `uv`          BIGINT          NOT NULL DEFAULT 0 COMMENT '独立访客数（HyperLogLog 估算）',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_date` (`short_code`, `stat_date`),
    KEY `idx_stat_date` (`stat_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='短链接天级访问统计表';
