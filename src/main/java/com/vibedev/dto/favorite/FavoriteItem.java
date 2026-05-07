package com.vibedev.dto.favorite;

import com.vibedev.dto.post.ShareCardResponse;

import java.time.Instant;

public record FavoriteItem(
        String id,
        String userId,
        String postId,
        String collectionFolderId,
        Instant createdAt,
        FavoritePostSummary post
) {
    public record FavoritePostSummary(
            String id,
            String title,
            String titleDisplay,
            String authorName,
            String boardName,
            boolean isDeleted
    ) {}
}
