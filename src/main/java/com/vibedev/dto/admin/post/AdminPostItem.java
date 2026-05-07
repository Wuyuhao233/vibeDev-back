package com.vibedev.dto.admin.post;

import com.vibedev.entity.Post;
import java.time.Instant;

public record AdminPostItem(
        String id, String title, String authorId, String boardId,
        boolean isDeleted, String deletedBy, String auditStatus,
        boolean isPinned, String pinType, boolean isEssence,
        int likeCount, int replyCount, int collectCount,
        int heatScore, Instant createdAt, Instant updatedAt
) {
    public static AdminPostItem from(Post p) {
        return new AdminPostItem(
                p.getId(), p.getTitle(), p.getAuthorId(), p.getBoardId(),
                p.isDeleted(), p.getDeletedBy(), p.getAuditStatus(),
                p.isPinned(), p.getPinType(), p.isEssence(),
                p.getLikeCount(), p.getReplyCount(), p.getCollectCount(),
                (int) p.getHeatScore(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
