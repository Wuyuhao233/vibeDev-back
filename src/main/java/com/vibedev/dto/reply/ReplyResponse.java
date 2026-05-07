package com.vibedev.dto.reply;

import com.vibedev.dto.user.UserSummary;

import java.time.Instant;
import java.util.List;

public record ReplyResponse(
        String id,
        String contentMarkdown,
        String contentHtml,
        String authorId,
        String postId,
        String parentReplyId,
        int depth,
        UserSummary author,
        boolean isLikedByCurrentUser,
        int likeCount,
        String auditStatus,
        boolean isDeleted,
        int version,
        Instant createdAt,
        Instant updatedAt,
        Instant lastEditedAt,
        List<ReplyResponse> childReplies
) {}
