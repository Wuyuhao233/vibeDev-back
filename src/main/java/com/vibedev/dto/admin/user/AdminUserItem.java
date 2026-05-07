package com.vibedev.dto.admin.user;

import com.vibedev.entity.User;
import java.time.Instant;

public record AdminUserItem(
        String id, String username, String avatarUrl, String role, int level,
        String email, boolean isBanned, boolean isActivated,
        int postCount, int replyCount, int points,
        Instant createdAt, Instant lastLoginAt, Instant bannedUntil
) {
    public static AdminUserItem from(User u) {
        return new AdminUserItem(
                u.getId(), u.getUsername(), u.getAvatarUrl(), u.getRole(), u.getLevel(),
                u.getEmail(), u.isBanned(), u.isActivated(),
                0, 0, u.getPoints(),
                u.getCreatedAt(), u.getLastLoginAt(), u.getBannedUntil()
        );
    }

    public static AdminUserItem from(User u, int postCount, int replyCount) {
        return new AdminUserItem(
                u.getId(), u.getUsername(), u.getAvatarUrl(), u.getRole(), u.getLevel(),
                u.getEmail(), u.isBanned(), u.isActivated(),
                postCount, replyCount, u.getPoints(),
                u.getCreatedAt(), u.getLastLoginAt(), u.getBannedUntil()
        );
    }
}
