package com.vibedev.dto.auth;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        int expiresIn
) {}
