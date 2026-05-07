package com.vibedev.dto.admin.post;

import jakarta.validation.constraints.NotBlank;

public record MovePostRequest(
        @NotBlank String targetBoardId
) {}
