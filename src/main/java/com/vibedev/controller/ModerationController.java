package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.moderation.*;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.ModerationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping("/admin/review-queue")
    public ApiResponse<ReviewQueueListResponse> getReviewQueue(
            @RequestParam(defaultValue = "pending") String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        var filter = new ReviewQueueFilter(status, targetType, page, pageSize);
        return ApiResponse.ok(moderationService.getReviewQueue(filter, userId, role));
    }

    @PostMapping("/admin/review-queue/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable String id, Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        moderationService.manualApprove(id, userId, role);
        return ApiResponse.ok();
    }

    @PostMapping("/admin/review-queue/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable String id,
                                    @Valid @RequestBody RejectRequest dto,
                                    Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        moderationService.manualReject(id, userId, role, dto.reason());
        return ApiResponse.ok();
    }

    @GetMapping("/admin/review-stats")
    public ApiResponse<ReviewStatsResponse> getReviewStats(Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        return ApiResponse.ok(moderationService.getReviewStats(userId, role));
    }

    @GetMapping("/admin/quality-audit/sample")
    public ApiResponse<QualityAuditSampleResponse> sampleQualityAudit(
            @RequestParam(defaultValue = "0.05") double rate) {
        return ApiResponse.ok(moderationService.sampleForQualityAudit(rate));
    }
}
