package com.swiftlink.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateLinkRequest {

    @NotBlank(message = "原始链接不能为空")
    @Size(max = 2048, message = "原始链接长度不能超过 2048")
    private String originUrl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @Size(max = 128, message = "业务标识长度不能超过 128")
    private String businessDomain;
}
