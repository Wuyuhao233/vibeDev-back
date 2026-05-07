package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mute_records")
public class MuteRecord {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "moderator_id", nullable = false, length = 36)
    private String moderatorId;

    @Column(name = "board_id", length = 36)
    private String boardId;

    @Column(nullable = false, length = 10)
    private String duration;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "unmuted_by", length = 36)
    private String unmutedBy;

    @Column(name = "unmuted_at")
    private Instant unmutedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getModeratorId() { return moderatorId; }
    public void setModeratorId(String moderatorId) { this.moderatorId = moderatorId; }
    public String getBoardId() { return boardId; }
    public void setBoardId(String boardId) { this.boardId = boardId; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public String getUnmutedBy() { return unmutedBy; }
    public void setUnmutedBy(String unmutedBy) { this.unmutedBy = unmutedBy; }
    public Instant getUnmutedAt() { return unmutedAt; }
    public void setUnmutedAt(Instant unmutedAt) { this.unmutedAt = unmutedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
