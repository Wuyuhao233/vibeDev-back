package com.vibedev.dto.appeal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAppealRequest(
        @NotBlank @Size(min = 5, max = 500)
        String reason
) {}
