package com.vibedev.dto.user;

import jakarta.validation.constraints.NotBlank;

public record BindCasRequest(
        @NotBlank String code,
        @NotBlank String redirectUri
) {}
