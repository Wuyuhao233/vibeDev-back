package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "moderation_queue")
public class ModerationQueue {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "target_type", nullable = false, length = 10)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 36)
    private String targetId;

    @Column(name = "content_snapshot", columnDefinition = "TEXT")
    private String contentSnapshot;

    @Column(name = "ai_score")
    private int aiScore;

    @Column(name = "ai_category", length = 50)
    private String aiCategory;

    @Column(name = "ai_degraded")
    private boolean aiDegraded = false;

    @Column(nullable = false)
    private int priority = 0;

    @Column(nullable = false, length = 10)
    private String status = "pending";

    @Column(name = "moderator_id", length = 36)
    private String moderatorId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "board_id", length = 36)
    private String boardId;

    @Column(name = "author_id", length = 36)
    private String authorId;

    @Column(name = "target_title", length = 100)
    private String targetTitle;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "handled_at")
    private Instant handledAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getContentSnapshot() { return contentSnapshot; }
    public void setContentSnapshot(String contentSnapshot) { this.contentSnapshot = contentSnapshot; }
    public int getAiScore() { return aiScore; }
    public void setAiScore(int aiScore) { this.aiScore = aiScore; }
    public String getAiCategory() { return aiCategory; }
    public void setAiCategory(String aiCategory) { this.aiCategory = aiCategory; }
    public boolean isAiDegraded() { return aiDegraded; }
    public void setAiDegraded(boolean aiDegraded) { this.aiDegraded = aiDegraded; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getModeratorId() { return moderatorId; }
    public void setModeratorId(String moderatorId) { this.moderatorId = moderatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getBoardId() { return boardId; }
    public void setBoardId(String boardId) { this.boardId = boardId; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getTargetTitle() { return targetTitle; }
    public void setTargetTitle(String targetTitle) { this.targetTitle = targetTitle; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getHandledAt() { return handledAt; }
    public void setHandledAt(Instant handledAt) { this.handledAt = handledAt; }
}
