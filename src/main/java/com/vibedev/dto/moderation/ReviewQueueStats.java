package com.vibedev.dto.moderation;

public record ReviewQueueStats(
        long pendingCount,
        long todayApproved,
        long todayRejected
) {}
