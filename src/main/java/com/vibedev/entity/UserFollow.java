package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_follows")
public class UserFollow {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "followed_user_id", nullable = false, length = 36)
    private String followedUserId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UserFollow() {}

    public UserFollow(String id, String userId, String followedUserId) {
        this.id = id;
        this.userId = userId;
        this.followedUserId = followedUserId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFollowedUserId() { return followedUserId; }
    public void setFollowedUserId(String followedUserId) { this.followedUserId = followedUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
