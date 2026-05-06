package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_export_tasks")
public class UserExportTask {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 20)
    private String scope = "all";

    @Column(nullable = false, length = 20)
    private String status = "processing";

    @Column(name = "download_url", length = 500)
    private String downloadUrl;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private int progress = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UserExportTask() {}

    public UserExportTask(String id, String userId, String scope) {
        this.id = id;
        this.userId = userId;
        this.scope = scope;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public String getScope() { return scope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public Instant getCreatedAt() { return createdAt; }
}
