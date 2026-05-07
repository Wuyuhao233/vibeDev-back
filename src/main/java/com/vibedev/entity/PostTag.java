package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "post_tags")
public class PostTag {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "post_id", nullable = false, length = 36)
    private String postId;

    @Column(name = "tag_id", nullable = false, length = 36)
    private String tagId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public PostTag() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    public String getTagId() { return tagId; }
    public void setTagId(String tagId) { this.tagId = tagId; }
    public Instant getCreatedAt() { return createdAt; }
}
