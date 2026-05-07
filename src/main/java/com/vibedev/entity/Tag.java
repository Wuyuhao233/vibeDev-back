package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tags")
public class Tag {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "board_id", nullable = false, length = 36)
    private String boardId;

    @Column(nullable = false, length = 10)
    private String name;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    @Column(name = "post_count")
    private int postCount = 0;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBoardId() { return boardId; }
    public void setBoardId(String boardId) { this.boardId = boardId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public int getPostCount() { return postCount; }
    public void setPostCount(int postCount) { this.postCount = postCount; }
    public Instant getCreatedAt() { return createdAt; }
}
