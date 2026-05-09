package com.vibedev.dto.user;

import java.time.Instant;

public record UserProfile(
        String id,
        String username,
        String email,
        String nickname,
        String signature,
        String avatarUrl,
        String role,
        int level,
        String levelTitle,
        int points,
        boolean isActivated,
        boolean isBanned,
        Instant bannedUntil,
        Instant createdAt,
        Instant lastLoginAt
) {}
