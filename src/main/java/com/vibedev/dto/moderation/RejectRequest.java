package com.vibedev.dto.moderation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @NotBlank @Size(min = 5, max = 500)
        String reason
) {}
