package com.vibedev.dto.post;

import com.vibedev.dto.board.TagItem;
import com.vibedev.dto.user.UserSummary;

import java.time.Instant;
import java.util.List;

public record PostDetailResponse(
        String id,
        String title,
        String contentMarkdown,
        String contentHtml,
        String authorId,
        String boardId,
        String boardName,
        List<TagItem> tags,
        String coverImageUrl,
        UserSummary author,
        boolean isCollectedByCurrentUser,
        boolean isLikedByCurrentUser,
        int likeCount,
        int replyCount,
        int collectCount,
        int shareCount,
        boolean isPinned,
        String pinType,
        boolean isEssence,
        String auditStatus,
        boolean isDeleted,
        int version,
        Instant createdAt,
        Instant updatedAt,
        Instant lastEditedAt
) {}
