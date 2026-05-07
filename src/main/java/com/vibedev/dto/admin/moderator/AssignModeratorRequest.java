package com.vibedev.dto.admin.moderator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignModeratorRequest(
        @NotBlank String userId,
        @NotNull List<String> boardIds
) {}
