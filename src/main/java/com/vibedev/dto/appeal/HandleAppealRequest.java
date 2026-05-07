package com.vibedev.dto.appeal;

import jakarta.validation.constraints.NotBlank;

public record HandleAppealRequest(
        @NotBlank
        String result,

        String note
) {}
