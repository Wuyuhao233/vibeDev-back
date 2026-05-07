package com.vibedev.dto.admin.moderator;

import jakarta.validation.constraints.NotBlank;

public record RemoveModeratorRequest(
        @NotBlank String userId,
        String note
) {}
