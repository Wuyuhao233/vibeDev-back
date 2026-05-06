package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.user.*;
import com.vibedev.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ─── Profile ──────────────────────────────────────────

    @GetMapping("/{username}")
    public ApiResponse<UserProfile> getProfile(@PathVariable String username,
                                                Authentication auth) {
        String viewerId = auth != null ? auth.getPrincipal().toString() : null;
        return ApiResponse.ok(userService.getProfile(username, viewerId));
    }

    @GetMapping("/{username}/posts")
    public ApiResponse<PaginatedResponse<UserPostItem>> getUserPosts(
            @PathVariable String username,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (pageSize > 50) pageSize = 50;
        return ApiResponse.ok(userService.getUserPosts(username, page, pageSize));
    }

    @GetMapping("/{username}/replies")
    public ApiResponse<PaginatedResponse<UserReplyItem>> getUserReplies(
            @PathVariable String username,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (pageSize > 50) pageSize = 50;
        return ApiResponse.ok(userService.getUserReplies(username, page, pageSize));
    }

    @GetMapping("/{username}/favorites")
    public ApiResponse<PaginatedResponse<UserCollectionItem>> getUserFavorites(
            @PathVariable String username,
            Authentication auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String viewerId = auth != null ? auth.getPrincipal().toString() : null;
        if (pageSize > 50) pageSize = 50;
        return ApiResponse.ok(userService.getUserFavorites(username, viewerId, page, pageSize));
    }

    @GetMapping("/{username}/history")
    public ApiResponse<PaginatedResponse<BrowseHistoryItem>> getUserHistory(
            @PathVariable String username,
            Authentication auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String viewerId = auth != null ? auth.getPrincipal().toString() : null;
        if (pageSize > 50) pageSize = 50;
        return ApiResponse.ok(userService.getUserHistory(username, viewerId, page, pageSize));
    }

    // ─── Profile Update ───────────────────────────────────

    @PatchMapping("/profile")
    public ApiResponse<UserSummary> updateProfile(@Valid @RequestBody UpdateProfileRequest req,
                                                    Authentication auth) {
        String userId = auth.getPrincipal().toString();
        return ApiResponse.ok(userService.updateProfile(userId, req));
    }

    @PostMapping("/me/avatar")
    public ApiResponse<AvatarUploadResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(userService.uploadAvatar(file));
    }

    // ─── Password ─────────────────────────────────────────

    @PutMapping("/password")
    public ApiResponse<ChangePasswordResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest req, Authentication auth) {
        String userId = auth.getPrincipal().toString();
        return ApiResponse.ok(userService.changePassword(userId, req));
    }

    // ─── Account Management ───────────────────────────────

    @PostMapping("/{username}/deactivate")
    public ApiResponse<DeactivateAccountResponse> deactivateAccount(
            @PathVariable String username,
            @Valid @RequestBody DeactivateAccountRequest req,
            Authentication auth) {
        String userId = auth.getPrincipal().toString();
        return ApiResponse.ok(userService.deactivateAccount(username, userId, req.password()));
    }

    @PostMapping("/{username}/recover")
    public ApiResponse<Void> recoverAccount(
            @PathVariable String username,
            @Valid @RequestBody RecoverAccountRequest req) {
        userService.recoverAccount(username, req);
        return ApiResponse.ok();
    }

    @PostMapping("/{username}/export-data")
    public ApiResponse<ExportDataResponse> exportData(
            @PathVariable String username,
            @Valid @RequestBody ExportDataRequest req,
            Authentication auth) {
        String userId = auth.getPrincipal().toString();
        return ApiResponse.ok(userService.exportData(username, userId, req));
    }

    @GetMapping("/{username}/export-data/{taskId}")
    public ApiResponse<ExportDataResponse> getExportStatus(
            @PathVariable String username,
            @PathVariable String taskId,
            Authentication auth) {
        String userId = auth.getPrincipal().toString();
        return ApiResponse.ok(userService.getExportStatus(username, userId, taskId));
    }

    // ─── Login History ────────────────────────────────────

    @GetMapping("/login-history")
    public ApiResponse<PaginatedResponse<LoginRecord>> getLoginHistory(
            Authentication auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String userId = auth.getPrincipal().toString();
        return ApiResponse.ok(userService.getLoginHistory(userId, page, pageSize));
    }

    // ─── Browsing Record ──────────────────────────────────

    @PostMapping("/me/browsing/{postId}")
    public ApiResponse<Void> recordBrowsing(@PathVariable String postId, Authentication auth) {
        String userId = auth.getPrincipal().toString();
        userService.recordBrowsing(userId, postId);
        return ApiResponse.ok();
    }
}
