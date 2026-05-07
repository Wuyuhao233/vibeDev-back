package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.admin.sensitive.*;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.SensitiveWordService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    public AdminSensitiveWordController(SensitiveWordService sensitiveWordService) {
        this.sensitiveWordService = sensitiveWordService;
    }

    @GetMapping("/sensitive-words")
    public ApiResponse<PaginatedResponse<SensitiveWordItem>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(sensitiveWordService.list(search, page, limit));
    }

    @PostMapping("/sensitive-words")
    public ApiResponse<SensitiveWordItem> add(@Valid @RequestBody AddSensitiveWordRequest dto,
                                              Authentication auth) {
        String createdBy = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(sensitiveWordService.add(dto.word(), dto.matchType(), createdBy));
    }

    @PutMapping("/sensitive-words/{id}")
    public ApiResponse<Void> update(@PathVariable String id,
                                     @Valid @RequestBody UpdateSensitiveWordRequest dto) {
        sensitiveWordService.update(id, dto);
        return ApiResponse.ok();
    }

    @DeleteMapping("/sensitive-words/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        sensitiveWordService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/sensitive-words/{id}/toggle")
    public ApiResponse<SensitiveWordItem> toggle(@PathVariable String id) {
        return ApiResponse.ok(sensitiveWordService.toggle(id));
    }

    @PostMapping("/sensitive-words/batch-import")
    public ApiResponse<List<SensitiveWordItem>> batchImport(
            @Valid @RequestBody BatchImportRequest dto, Authentication auth) {
        String createdBy = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(sensitiveWordService.batchImport(dto.words(), createdBy));
    }
}
