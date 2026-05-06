package com.vibedev.dto.auth;

import com.vibedev.dto.user.UserSummary;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        int expiresIn,
        UserSummary user
) {}
