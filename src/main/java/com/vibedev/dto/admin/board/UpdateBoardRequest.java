package com.vibedev.dto.admin.board;

import jakarta.validation.constraints.Size;

public record UpdateBoardRequest(
        @Size(min = 2, max = 20) String name,
        String icon,
        @Size(max = 200) String description,
        Integer sortOrder
) {}
