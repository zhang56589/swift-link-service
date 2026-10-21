package com.swiftlink.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LinkStatsVO {

    private String shortCode;

    private LocalDate date;

    private long pv;

    private long uv;

    private long totalPv;

    private long totalUv;
}
