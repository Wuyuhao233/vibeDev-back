package com.vibedev.dto.user;

import java.time.Instant;

public record BrowseHistoryItem(
        String postId,
        String postTitle,
        Instant viewedAt
) {}
