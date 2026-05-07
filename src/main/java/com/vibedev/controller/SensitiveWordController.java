package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.sensitive.SensitiveCheckRequest;
import com.vibedev.service.SensitiveWordService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    public SensitiveWordController(SensitiveWordService sensitiveWordService) {
        this.sensitiveWordService = sensitiveWordService;
    }

    @PostMapping("/content/check-sensitive")
    public ApiResponse<Object> checkSensitive(@RequestBody SensitiveCheckRequest request) {
        return ApiResponse.ok(sensitiveWordService.check(request));
    }
}
