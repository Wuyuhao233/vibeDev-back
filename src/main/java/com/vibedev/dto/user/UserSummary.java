package com.vibedev.dto.user;

import com.vibedev.entity.User;

public record UserSummary(
        String id,
        String username,
        String nickname,
        String avatarUrl,
        int level,
        String role
) {
    public static UserSummary from(User u) {
        return new UserSummary(
                u.getId(), u.getUsername(), u.getNickname(),
                u.getAvatarUrl(), u.getLevel(), u.getRole());
    }
}
