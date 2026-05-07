package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.appeal.AppealItem;
import com.vibedev.dto.appeal.CreateAppealRequest;
import com.vibedev.dto.appeal.HandleAppealRequest;
import com.vibedev.dto.report.*;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/reports")
    public ApiResponse<Void> create(@RequestBody CreateReportRequest dto, Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        reportService.create(userId, dto);
        return ApiResponse.ok();
    }

    @GetMapping("/reports/check-ban")
    public ApiResponse<CheckBanResponse> checkBan(Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(reportService.checkBan(userId));
    }

    @PostMapping("/reports/{id}/appeal")
    public ApiResponse<Void> appeal(@PathVariable String id,
                                    @Valid @RequestBody CreateAppealRequest dto,
                                    Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        reportService.submitAppeal(id, userId, dto);
        return ApiResponse.ok();
    }

    @GetMapping("/admin/reports")
    public ApiResponse<PaginatedResponse<ReportItem>> list(
            @RequestParam(defaultValue = "pending") String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String boardId,
            Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        return ApiResponse.ok(reportService.list(status, targetType, page, pageSize, userId, role, boardId));
    }

    @GetMapping("/admin/reports/{id}")
    public ApiResponse<ReportItem> getById(@PathVariable String id) {
        return ApiResponse.ok(reportService.getById(id));
    }

    @PutMapping("/admin/reports/{id}/handle")
    public ApiResponse<Void> handle(@PathVariable String id, @RequestBody HandleReportRequest dto,
                                     Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        reportService.handle(id, dto, userId, role);
        return ApiResponse.ok();
    }

    @GetMapping("/admin/reports/stats")
    public ApiResponse<ReportStatsResponse> stats() {
        return ApiResponse.ok(reportService.stats());
    }

    @GetMapping("/admin/appeals")
    public ApiResponse<PaginatedResponse<AppealItem>> listAppeals(
            @RequestParam(defaultValue = "pending") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(reportService.listAppeals(status, page, pageSize));
    }

    @PostMapping("/admin/appeals/{id}/approve")
    public ApiResponse<Void> approveAppeal(@PathVariable String id, Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        reportService.approveAppeal(id, userId);
        return ApiResponse.ok();
    }

    @PostMapping("/admin/appeals/{id}/reject")
    public ApiResponse<Void> rejectAppeal(@PathVariable String id,
                                          @Valid @RequestBody HandleAppealRequest dto,
                                          Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        reportService.rejectAppeal(id, userId, dto.note());
        return ApiResponse.ok();
    }
}
