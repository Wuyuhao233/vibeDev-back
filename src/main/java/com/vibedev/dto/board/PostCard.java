package com.vibedev.dto.board;

import java.time.Instant;
import java.util.List;

public record PostCard(
        String id,
        String title,
        String contentSummary,
        String coverImageUrl,
        PostCardAuthor author,
        Instant createdAt,
        int likeCount,
        int replyCount,
        int collectCount,
        List<TagItem> tags,
        boolean isPinned,
        boolean isEssence,
        String boardName,
        boolean isLiked,
        boolean isCollected) {
}
