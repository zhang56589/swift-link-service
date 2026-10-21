package com.swiftlink.controller;

import com.swiftlink.dto.CreateLinkRequest;
import com.swiftlink.dto.CreateLinkResponse;
import com.swiftlink.dto.LinkDetailVO;
import com.swiftlink.dto.Result;
import com.swiftlink.service.ShortLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkController {

    private final ShortLinkService shortLinkService;

    @PostMapping
    public Result<CreateLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        return Result.ok(shortLinkService.create(request));
    }

    @GetMapping("/{shortCode}")
    public Result<LinkDetailVO> detail(@PathVariable String shortCode) {
        return Result.ok(shortLinkService.detail(shortCode));
    }

    @PutMapping("/{shortCode}/status")
    public Result<Void> updateStatus(@PathVariable String shortCode, @RequestParam Integer status) {
        shortLinkService.updateStatus(shortCode, status);
        return Result.ok();
    }
}
