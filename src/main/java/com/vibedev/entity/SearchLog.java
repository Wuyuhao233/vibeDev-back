package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "search_logs")
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(nullable = false, length = 20)
    private String scope = "all";

    @Column(name = "board_id", length = 36)
    private String boardId;

    @Column(name = "result_count")
    private int resultCount = 0;

    @Column(name = "search_time_ms")
    private double searchTimeMs = 0;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getBoardId() { return boardId; }
    public void setBoardId(String boardId) { this.boardId = boardId; }
    public int getResultCount() { return resultCount; }
    public void setResultCount(int resultCount) { this.resultCount = resultCount; }
    public double getSearchTimeMs() { return searchTimeMs; }
    public void setSearchTimeMs(double searchTimeMs) { this.searchTimeMs = searchTimeMs; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
