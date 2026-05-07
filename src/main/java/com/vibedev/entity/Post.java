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

    @Column(name = "heat_score", precision = 10, scale = 4)
    private double heatScore = 0;

    @Column(name = "content_length")
    private int contentLength = 0;

    @Column(name = "has_code_block")
    private boolean hasCodeBlock = false;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Version
    private int version = 0;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContentMarkdown() { return contentMarkdown; }
    public void setContentMarkdown(String contentMarkdown) { this.contentMarkdown = contentMarkdown; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getBoardId() { return boardId; }
    public void setBoardId(String boardId) { this.boardId = boardId; }
    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public int getReplyCount() { return replyCount; }
    public void setReplyCount(int replyCount) { this.replyCount = replyCount; }
    public int getCollectCount() { return collectCount; }
    public void setCollectCount(int collectCount) { this.collectCount = collectCount; }
    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }
    public boolean isEssence() { return isEssence; }
    public void setEssence(boolean essence) { isEssence = essence; }
    public String getAuditStatus() { return auditStatus; }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; }
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Instant getLastEditedAt() { return lastEditedAt; }
    public void setLastEditedAt(Instant lastEditedAt) { this.lastEditedAt = lastEditedAt; }
    public double getHeatScore() { return heatScore; }
    public void setHeatScore(double heatScore) { this.heatScore = heatScore; }
    public int getContentLength() { return contentLength; }
    public void setContentLength(int contentLength) { this.contentLength = contentLength; }
    public boolean isHasCodeBlock() { return hasCodeBlock; }
    public void setHasCodeBlock(boolean hasCodeBlock) { this.hasCodeBlock = hasCodeBlock; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
