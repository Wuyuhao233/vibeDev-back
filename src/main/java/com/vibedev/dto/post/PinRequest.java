package com.vibedev.dto.post;

import jakarta.validation.constraints.NotBlank;

public record PinRequest(
        @NotBlank String pinType
) {}
