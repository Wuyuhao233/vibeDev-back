package com.vibedev.dto.board;

import java.time.Instant;
import java.util.List;

public record BoardItem(
        String id,
        String name,
        String icon,
        String description,
        int sortOrder,
        String status,
        int postCount,
        Instant createdAt,
        List<TagItem> tags) {
}
