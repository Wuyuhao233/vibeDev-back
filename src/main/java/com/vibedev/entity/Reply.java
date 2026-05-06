package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "replies")
public class Reply {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "content_markdown", columnDefinition = "MEDIUMTEXT")
    private String contentMarkdown;

    @Column(name = "author_id", nullable = false, length = 36)
    private String authorId;

    @Column(name = "post_id", nullable = false, length = 36)
    private String postId;

    @Column(name = "like_count")
    private int likeCount = 0;

    @Column(name = "audit_status", length = 10)
    private String auditStatus = "approved";

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public String getContentMarkdown() { return contentMarkdown; }
    public String getAuthorId() { return authorId; }
    public String getPostId() { return postId; }
    public int getLikeCount() { return likeCount; }
    public boolean isDeleted() { return isDeleted; }
    public Instant getCreatedAt() { return createdAt; }
}
