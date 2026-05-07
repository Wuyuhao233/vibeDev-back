package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "moderation_log")
public class ModerationLog {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "target_type", nullable = false, length = 10)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 36)
    private String targetId;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "operator_id", length = 36)
    private String operatorId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

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
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCreatedAt() { return createdAt; }
}
