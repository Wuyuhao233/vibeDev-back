package com.vibedev.dto.admin.board;

import jakarta.validation.constraints.Size;

public record UpdateTagRequest(
        @Size(min = 2, max = 10) String name,
        Integer sortOrder
) {}
