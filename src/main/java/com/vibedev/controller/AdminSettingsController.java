package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.admin.settings.SystemSettingsData;
import com.vibedev.dto.admin.settings.UpdateSettingRequest;
import com.vibedev.service.SystemSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSettingsController {

    private final SystemSettingsService settingsService;

    public AdminSettingsController(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/settings")
    public ApiResponse<SystemSettingsData> getAll() {
        Map<String, String> settings = settingsService.getAll();
        return ApiResponse.ok(new SystemSettingsData(settings));
    }

    @PutMapping("/settings/{key}")
    public ApiResponse<Void> update(@PathVariable String key,
                                     @Valid @RequestBody UpdateSettingRequest dto) {
        settingsService.update(key, dto.configValue());
        return ApiResponse.ok();
    }
}
