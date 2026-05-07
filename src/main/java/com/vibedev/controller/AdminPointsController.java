package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.service.PointsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPointsController {

    private final PointsService pointsService;

    public AdminPointsController(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @PostMapping("/points/recalculate")
    public ApiResponse<Map<String, Object>> recalculate() {
        int updatedUsers = pointsService.recalculateAllPoints();
        return ApiResponse.ok(Map.of("updatedUsers", updatedUsers));
    }
}
