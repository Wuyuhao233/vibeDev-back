package com.vibedev.dto.moderation;

public record ReviewQueueFilter(
        String status,
        String targetType,
        int page,
        int pageSize
) {
    public ReviewQueueFilter {
        if (status == null || status.isBlank()) status = "pending";
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 50) pageSize = 20;
    }
}
