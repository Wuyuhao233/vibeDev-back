package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.notification.NotificationListResponse;
import com.vibedev.dto.notification.RecentNotificationsResponse;
import com.vibedev.dto.notification.UnreadCountResponse;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(notificationService.list(userId, filter, page, limit));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(notificationService.getUnreadCount(userId));
    }

    @GetMapping("/notifications/recent")
    public ApiResponse<RecentNotificationsResponse> recent(Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(notificationService.getRecent(userId));
    }

    @PutMapping("/notifications/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable String id, Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        notificationService.markRead(userId, id);
        return ApiResponse.ok();
    }

    @PutMapping("/notifications/read-all")
    public ApiResponse<Void> markAllRead(Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        notificationService.markAllRead(userId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/notifications/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        notificationService.delete(userId, id);
        return ApiResponse.ok();
    }
}
