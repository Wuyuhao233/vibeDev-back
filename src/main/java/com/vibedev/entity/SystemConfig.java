package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "system_configs")
public class SystemConfig {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "config_key", nullable = false, length = 100, unique = true)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column(length = 500)
    private String description;

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
    public String getConfigKey() { return configKey; }
    public String getConfigValue() { return configValue; }
    public String getDescription() { return description; }
}
