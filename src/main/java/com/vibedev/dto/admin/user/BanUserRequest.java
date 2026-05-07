package com.vibedev.dto.admin.user;

import jakarta.validation.constraints.NotBlank;

public record BanUserRequest(
        @NotBlank String reason
) {}
