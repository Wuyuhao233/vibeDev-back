package com.vibedev.dto.notification;

import com.vibedev.entity.Notification;

import java.time.Instant;

public record NotificationItem(
        String id,
        String eventType,
        String title,
        String body,
        String link,
        boolean isRead,
        Instant createdAt
) {
    public static NotificationItem from(Notification n) {
        String link = null;
        if (n.getSourceType() != null && n.getSourceId() != null) {
            if ("post".equals(n.getSourceType())) {
                link = "/post/" + n.getSourceId();
            } else if ("reply".equals(n.getSourceType())) {
                link = "/post/" + n.getSourceId() + "#reply-" + n.getSourceId();
            }
        }
        return new NotificationItem(
                n.getId(),
                n.getEventType(),
                n.getTitle(),
                n.getContent(),
                link,
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
