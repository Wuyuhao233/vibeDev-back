package com.vibedev.dto.admin.post;

import jakarta.validation.constraints.NotBlank;

public record PinPostRequest(
        @NotBlank String pinType
) {}
