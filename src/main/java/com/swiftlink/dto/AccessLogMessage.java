package com.swiftlink.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccessLogMessage {

    private String shortCode;

    private String clientIp;

    private String userAgent;

    private String referer;

    private LocalDateTime accessTime;
}
