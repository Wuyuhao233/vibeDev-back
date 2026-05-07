package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.service.PointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPointsController {

    private static final Logger log = LoggerFactory.getLogger(AdminPointsController.class);

    private final PointsService pointsService;

    public AdminPointsController(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @PostMapping("/points/recalculate")
    public ApiResponse<Map<String, Object>> recalculate() {
        int updatedUsers = pointsService.recalculateAllPoints();
        return ApiResponse.ok(Map.of("updatedUsers", updatedUsers));
    }

    @PostMapping("/recalculate-points")
    public ApiResponse<Map<String, Object>> recalculatePoints() {
        String taskId = "task_recalc_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Recalculate points triggered, taskId={}", taskId);
        int updatedUsers = pointsService.recalculateAllPoints();
        return ApiResponse.ok(Map.of(
                "task_id", taskId,
                "status", "completed",
                "updated_users", updatedUsers
        ));
    }
}
