package com.vibedev.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record CasLoginParams(
        @NotBlank String ticket,
        @NotBlank String service
) {}
