package com.vibedev.dto.notification;

import java.util.List;

public record RecentNotificationsResponse(
        List<NotificationItem> items,
        long unreadCount
) {}
