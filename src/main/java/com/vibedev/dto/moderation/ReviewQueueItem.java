package com.vibedev.dto.moderation;

import com.vibedev.dto.user.UserSummary;
import com.vibedev.entity.ModerationQueue;

import java.time.Instant;

public record ReviewQueueItem(
        String id,
        String targetType,
        String targetId,
        String targetTitle,
        String contentExcerpt,
        UserSummary author,
        String boardName,
        int aiScore,
        String aiCategory,
        String aiSnippet,
        boolean aiDegraded,
        String status,
        int priority,
        Instant createdAt
) {
    public static ReviewQueueItem from(ModerationQueue mq, UserSummary author, String boardName) {
        String excerpt = mq.getContentSnapshot() != null ?
                (mq.getContentSnapshot().length() > 200 ?
                        mq.getContentSnapshot().substring(0, 200) : mq.getContentSnapshot()) : "";
        return new ReviewQueueItem(
                mq.getId(),
                mq.getTargetType(),
                mq.getTargetId(),
                mq.getTargetTitle(),
                excerpt,
                author,
                boardName,
                mq.getAiScore(),
                mq.getAiCategory() != null ? mq.getAiCategory() : "normal",
                mq.getAiSnippet(),
                mq.isAiDegraded(),
                mq.getStatus(),
                mq.getPriority(),
                mq.getCreatedAt()
        );
    }
}
