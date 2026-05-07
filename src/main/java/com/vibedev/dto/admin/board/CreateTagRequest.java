package com.vibedev.dto.admin.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTagRequest(
        @NotBlank @Size(min = 2, max = 10) String name
) {}
