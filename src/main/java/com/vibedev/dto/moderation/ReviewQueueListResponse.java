package com.vibedev.dto.moderation;

import java.util.List;

public record ReviewQueueListResponse(
        List<ReviewQueueItem> items,
        ReviewQueueStats stats,
        long total,
        int page,
        int pageSize
) {}
