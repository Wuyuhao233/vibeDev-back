package com.vibedev.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_audit_log")
public class AiAuditLog {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "target_type", nullable = false, length = 10)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 36)
    private String targetId;

    @Column(name = "api_provider", length = 50)
    private String apiProvider = "mock";

    @Column(name = "request_chars")
    private int requestChars;

    @Column(name = "response_score")
    private int responseScore;

    @Column(name = "response_category", length = 50)
    private String responseCategory;

    @Column(name = "is_degraded")
    private boolean isDegraded = false;

    @Column(precision = 10, scale = 6)
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(name = "response_time_ms")
    private int responseTimeMs;

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
    public String getApiProvider() { return apiProvider; }
    public void setApiProvider(String apiProvider) { this.apiProvider = apiProvider; }
    public int getRequestChars() { return requestChars; }
    public void setRequestChars(int requestChars) { this.requestChars = requestChars; }
    public int getResponseScore() { return responseScore; }
    public void setResponseScore(int responseScore) { this.responseScore = responseScore; }
    public String getResponseCategory() { return responseCategory; }
    public void setResponseCategory(String responseCategory) { this.responseCategory = responseCategory; }
    public boolean isDegraded() { return isDegraded; }
    public void setDegraded(boolean degraded) { isDegraded = degraded; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public int getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(int responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    public Instant getCreatedAt() { return createdAt; }
}
