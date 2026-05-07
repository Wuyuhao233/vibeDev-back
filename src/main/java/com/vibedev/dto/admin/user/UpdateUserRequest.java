package com.vibedev.dto.admin.user;

public record UpdateUserRequest(
        String role,
        Integer level
) {}
