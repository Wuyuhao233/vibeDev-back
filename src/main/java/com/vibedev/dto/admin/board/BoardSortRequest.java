package com.vibedev.dto.admin.board;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BoardSortRequest(
        @NotNull List<BoardSortItem> items
) {}
