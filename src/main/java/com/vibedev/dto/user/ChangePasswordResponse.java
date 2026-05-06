package com.vibedev.dto.user;

public record ChangePasswordResponse(
        String message,
        int sessionsTerminated
) {}
