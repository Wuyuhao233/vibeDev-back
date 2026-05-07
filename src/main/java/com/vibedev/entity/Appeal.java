package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "appeals")
public class Appeal {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "report_id", nullable = false, length = 36)
    private String reportId;

    @Column(name = "appellant_id", nullable = false, length = 36)
    private String appellantId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 10)
    private String status = "pending";

    @Column(name = "handler_id", length = 36)
    private String handlerId;

    @Column(name = "handler_note", columnDefinition = "TEXT")
    private String handlerNote;

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

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getAppellantId() { return appellantId; }
    public void setAppellantId(String appellantId) { this.appellantId = appellantId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getHandlerId() { return handlerId; }
    public void setHandlerId(String handlerId) { this.handlerId = handlerId; }

    public String getHandlerNote() { return handlerNote; }
    public void setHandlerNote(String handlerNote) { this.handlerNote = handlerNote; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
