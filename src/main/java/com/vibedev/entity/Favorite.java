package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "favorites")
public class Favorite {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "post_id", nullable = false, length = 36)
    private String postId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getPostId() { return postId; }
    public Instant getCreatedAt() { return createdAt; }
}
