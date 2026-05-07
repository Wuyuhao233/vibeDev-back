package com.vibedev.dto.admin.sensitive;

import com.vibedev.entity.SensitiveWord;
import java.time.Instant;

public record SensitiveWordItem(
        String id, String word, String matchType, boolean isActive,
        String createdBy, Instant createdAt
) {
    public static SensitiveWordItem from(SensitiveWord sw) {
        return new SensitiveWordItem(
                sw.getId(), sw.getWord(), sw.getMatchType(), sw.isActive(),
                sw.getCreatedBy(), sw.getCreatedAt()
        );
    }
}
