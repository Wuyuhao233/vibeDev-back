package com.vibedev.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePostRequest(
        @NotBlank String boardId,
        @Size(max = 3) List<String> tagNames,
        @NotBlank @Size(min = 5, max = 100) String title,
        @NotBlank String content,
        String coverImageUrl,
        @NotBlank String idempotencyKey
) {}
