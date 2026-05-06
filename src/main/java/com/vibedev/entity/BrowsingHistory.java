package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "browsing_history")
public class BrowsingHistory {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "post_id", nullable = false, length = 36)
    private String postId;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    @PrePersist
    void onCreate() {
        if (viewedAt == null) {
            viewedAt = Instant.now();
        }
    }

    public BrowsingHistory() {}

    public BrowsingHistory(String id, String userId, String postId) {
        this.id = id;
        this.userId = userId;
        this.postId = postId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public String getPostId() { return postId; }
    public Instant getViewedAt() { return viewedAt; }
    public void setViewedAt(Instant viewedAt) { this.viewedAt = viewedAt; }
}
