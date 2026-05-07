package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "moderator_boards", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "board_id"})
})
public class ModeratorBoard {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "board_id", nullable = false, length = 36)
    private String boardId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getBoardId() { return boardId; }
    public void setBoardId(String boardId) { this.boardId = boardId; }
    public Instant getCreatedAt() { return createdAt; }
}
