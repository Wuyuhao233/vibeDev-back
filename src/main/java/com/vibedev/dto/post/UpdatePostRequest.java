package com.vibedev.dto.post;

import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdatePostRequest(
        @Size(min = 5, max = 100) String title,
        String content,
        @Size(min = 1, max = 3) List<String> tagIds,
        String coverImageUrl,
        int version
) {}
