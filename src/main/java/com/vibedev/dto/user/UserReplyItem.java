package com.vibedev.dto.user;

import java.time.Instant;

public record UserReplyItem(
        String id,
        String postId,
        String postTitle,
        String contentSummary,
        int likeCount,
        boolean isDeleted,
        Instant createdAt
) {}
