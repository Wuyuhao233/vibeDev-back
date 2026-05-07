package com.vibedev.dto.points;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vibedev.entity.PointsLog;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PointsLogItem(
        String id,
        int amount,
        String reason,
        @JsonProperty("reason_label") String reasonLabel,
        @JsonProperty("related_type") String relatedType,
        @JsonProperty("related_id") String relatedId,
        @JsonProperty("related_title") String relatedTitle,
        @JsonProperty("created_at") Instant createdAt
) {
    public static PointsLogItem from(PointsLog log, String relatedTitle) {
        return new PointsLogItem(
                log.getId(),
                log.getAmount(),
                log.getReason(),
                reasonLabel(log.getReason()),
                log.getRelatedType(),
                log.getRelatedId(),
                relatedTitle,
                log.getCreatedAt()
        );
    }

    private static String reasonLabel(String reason) {
        return switch (reason) {
            case "create_post" -> "发布帖子";
            case "create_reply" -> "发布回复";
            case "receive_like" -> "收到点赞";
            case "receive_bookmark" -> "帖子被收藏";
            case "post_featured" -> "帖子被加精";
            case "checkin" -> "每日签到";
            case "post_deleted_self" -> "自行删除帖子";
            case "post_deleted_mod" -> "帖子被管理员删除";
            case "reply_deleted_mod" -> "回复被管理员删除";
            case "muted_penalty" -> "禁言扣分";
            case "admin_adjust" -> "管理员手动调整";
            case "level_change" -> "等级变动";
            default -> reason;
        };
    }
}
