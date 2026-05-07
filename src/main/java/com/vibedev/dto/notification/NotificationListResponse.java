package com.vibedev.dto.notification;

import java.util.List;

public record NotificationListResponse(
        List<NotificationItem> items,
        long unreadCount,
        long total,
        int page,
        int pageSize
) {}
