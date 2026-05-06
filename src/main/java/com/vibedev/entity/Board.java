package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "boards")
public class Board {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(length = 500)
    private String icon = "";

    @Column(length = 200)
    private String description = "";

    @Column(name = "sort_order")
    private int sortOrder = 0;

    @Column(nullable = false, length = 10)
    private String status = "active";

    @Column(name = "post_count")
    private int postCount = 0;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private int version = 0;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public String getName() { return name; }
}
