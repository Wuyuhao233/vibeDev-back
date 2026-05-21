package com.vibedev.dto.admin.sensitive;

import jakarta.validation.constraints.NotBlank;

public record AddSensitiveWordRequest(
        @NotBlank String word,
        String matchType,
        String category
) {}
