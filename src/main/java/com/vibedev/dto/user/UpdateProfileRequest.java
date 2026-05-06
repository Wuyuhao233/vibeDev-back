package com.vibedev.dto.user;

public record UpdateProfileRequest(
        String nickname,
        String signature,
        String avatarUrl
) {}
