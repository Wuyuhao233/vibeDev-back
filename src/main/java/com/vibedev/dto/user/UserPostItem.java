package com.vibedev.dto.user;

import java.time.Instant;
import java.util.List;

public record UserPostItem(
        String id,
        String title,
        String boardName,
        List<String> tags,
        int likeCount,
        int replyCount,
        int collectCount,
        boolean isEssenced,
        boolean isDeleted,
        Instant createdAt,
        Instant updatedAt
) {}
