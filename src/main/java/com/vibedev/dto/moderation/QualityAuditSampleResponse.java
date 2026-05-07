package com.vibedev.dto.moderation;

import java.time.Instant;
import java.util.List;

public record QualityAuditSampleResponse(
        List<SampleItem> items,
        int sampleSize,
        long totalApprovedInMonth,
        double sampleRate
) {
    public record SampleItem(
            String postId,
            String title,
            String authorId,
            String boardId,
            Instant createdAt,
            int replyCount,
            int likeCount
    ) {}
}
