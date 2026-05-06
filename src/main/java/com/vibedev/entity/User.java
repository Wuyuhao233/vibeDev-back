package com.vibedev.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 20, unique = true)
    private String username;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(length = 200)
    private String signature = "";

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl = "";

    @Column(nullable = false, length = 10)
    private String role = "user";

    @Column(nullable = false)
    private int level = 1;

    @Column(nullable = false)
    private int points = 0;

    @Column(name = "is_activated", nullable = false)
    private boolean isActivated = false;

    @Column(name = "is_banned", nullable = false)
    private boolean isBanned = false;

    @Column(name = "banned_until")
    private Instant bannedUntil;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private int version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount = 0;

    @Column(name = "login_locked_until")
    private Instant loginLockedUntil;

    @Column(name = "is_deactivated", nullable = false)
    private boolean isDeactivated = false;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(name = "recovery_deadline")
    private Instant recoveryDeadline;

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
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public boolean isActivated() { return isActivated; }
    public void setActivated(boolean activated) { isActivated = activated; }
    public boolean isBanned() { return isBanned; }
    public void setBanned(boolean banned) { isBanned = banned; }
    public Instant getBannedUntil() { return bannedUntil; }
    public void setBannedUntil(Instant bannedUntil) { this.bannedUntil = bannedUntil; }
    public int getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(int tokenVersion) { this.tokenVersion = tokenVersion; }
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public int getLoginFailCount() { return loginFailCount; }
    public void setLoginFailCount(int loginFailCount) { this.loginFailCount = loginFailCount; }
    public Instant getLoginLockedUntil() { return loginLockedUntil; }
    public void setLoginLockedUntil(Instant loginLockedUntil) { this.loginLockedUntil = loginLockedUntil; }
    public boolean isDeactivated() { return isDeactivated; }
    public void setDeactivated(boolean deactivated) { isDeactivated = deactivated; }
    public Instant getDeactivatedAt() { return deactivatedAt; }
    public void setDeactivatedAt(Instant deactivatedAt) { this.deactivatedAt = deactivatedAt; }
    public Instant getRecoveryDeadline() { return recoveryDeadline; }
    public void setRecoveryDeadline(Instant recoveryDeadline) { this.recoveryDeadline = recoveryDeadline; }
}
