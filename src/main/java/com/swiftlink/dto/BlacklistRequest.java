package com.swiftlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BlacklistRequest {

    @NotBlank(message = "黑名单内容不能为空")
    @Size(max = 128, message = "黑名单内容长度不能超过 128")
    private String value;
}
