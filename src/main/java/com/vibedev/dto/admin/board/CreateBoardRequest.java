package com.vibedev.dto.admin.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateBoardRequest(
        @NotBlank @Size(min = 2, max = 20) String name,
        String icon,
        @Size(max = 200) String description,
        List<String> tags
) {}
