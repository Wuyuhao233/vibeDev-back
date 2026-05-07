package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.admin.apiusage.ApiUsageResponse;
import com.vibedev.dto.admin.dashboard.DashboardStatsResponse;
import com.vibedev.service.ApiUsageService;
import com.vibedev.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final ApiUsageService apiUsageService;

    public AdminDashboardController(DashboardService dashboardService,
                                     ApiUsageService apiUsageService) {
        this.dashboardService = dashboardService;
        this.apiUsageService = apiUsageService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardStatsResponse> getStats() {
        return ApiResponse.ok(dashboardService.getStats());
    }

    @GetMapping("/api-usage")
    public ApiResponse<ApiUsageResponse> getApiUsage(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(apiUsageService.getApiUsage(days));
    }
}
