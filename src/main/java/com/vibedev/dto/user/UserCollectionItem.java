package com.vibedev.dto.user;

import java.time.Instant;
import java.util.List;

public record UserCollectionItem(
        String id,
        String postId,
        String postTitle,
        String postAuthor,
        String boardName,
        List<String> tags,
        boolean isPostDeleted,
        Instant collectedAt
) {}
