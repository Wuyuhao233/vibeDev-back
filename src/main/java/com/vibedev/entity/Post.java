package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "content_markdown", columnDefinition = "MEDIUMTEXT")
    private String contentMarkdown;

    @Column(name = "author_id", nullable = false, length = 36)
    private String authorId;

    @Column(name = "board_id", nullable = false, length = 36)
    private String boardId;

    @Column(name = "like_count")
    private int likeCount = 0;

    @Column(name = "reply_count")
    private int replyCount = 0;

    @Column(name = "collect_count")
    private int collectCount = 0;

    @Column(name = "is_pinned")
    private boolean isPinned = false;

    @Column(name = "is_essence")
    private boolean isEssence = false;

    @Column(name = "audit_status", length = 10)
    private String auditStatus = "approved";

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "last_edited_at")
    private Instant lastEditedAt;

    @Version
    private int version = 0;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthorId() { return authorId; }
    public String getBoardId() { return boardId; }
    public int getLikeCount() { return likeCount; }
    public int getReplyCount() { return replyCount; }
    public int getCollectCount() { return collectCount; }
    public boolean isEssence() { return isEssence; }
    public boolean isDeleted() { return isDeleted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastEditedAt() { return lastEditedAt; }
}
