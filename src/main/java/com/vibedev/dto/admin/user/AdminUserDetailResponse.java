package com.vibedev.dto.admin.user;

import com.vibedev.entity.User;
import java.time.Instant;

public record AdminUserDetailResponse(
        String id, String username, String nickname, String email,
        String avatarUrl, String role, int level, int points,
        String signature, boolean isActivated, boolean isBanned,
        boolean isDeactivated, Instant bannedUntil,
        int loginFailCount, Instant loginLockedUntil,
        Instant createdAt, Instant updatedAt, Instant lastLoginAt
) {
    public static AdminUserDetailResponse from(User u) {
        return new AdminUserDetailResponse(
                u.getId(), u.getUsername(), u.getNickname(), u.getEmail(),
                u.getAvatarUrl(), u.getRole(), u.getLevel(), u.getPoints(),
                u.getSignature(), u.isActivated(), u.isBanned(),
                u.isDeactivated(), u.getBannedUntil(),
                u.getLoginFailCount(), u.getLoginLockedUntil(),
                u.getCreatedAt(), u.getUpdatedAt(), u.getLastLoginAt()
        );
    }
}
