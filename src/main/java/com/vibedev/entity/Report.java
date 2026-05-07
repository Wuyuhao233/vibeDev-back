package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reports")
public class Report {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "reporter_id", nullable = false, length = 36)
    private String reporterId;

    @Column(name = "target_type", nullable = false, length = 10)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 36)
    private String targetId;

    @Column(name = "reason_type", nullable = false, length = 20)
    private String reasonType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 10)
    private String status = "pending";

    @Column(length = 10)
    private String result;

    @Column(name = "result_description", columnDefinition = "TEXT")
    private String resultDescription;

    @Column(name = "handler_id", length = 36)
    private String handlerId;

    @Column(name = "is_malicious")
    private boolean isMalicious = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getReasonType() { return reasonType; }
    public void setReasonType(String reasonType) { this.reasonType = reasonType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getResultDescription() { return resultDescription; }
    public void setResultDescription(String resultDescription) { this.resultDescription = resultDescription; }
    public String getHandlerId() { return handlerId; }
    public void setHandlerId(String handlerId) { this.handlerId = handlerId; }
    public boolean isMalicious() { return isMalicious; }
    public void setMalicious(boolean malicious) { isMalicious = malicious; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
