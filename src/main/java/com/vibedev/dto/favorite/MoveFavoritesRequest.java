package com.vibedev.dto.favorite;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MoveFavoritesRequest(
        @NotNull @Size(min = 1) List<String> postIds,
        String targetFolderId
) {}
