package com.vibedev.dto.favorite;

import java.time.Instant;

public record FolderResponse(
        String id,
        String userId,
        String name,
        int version,
        Instant createdAt
) {}
