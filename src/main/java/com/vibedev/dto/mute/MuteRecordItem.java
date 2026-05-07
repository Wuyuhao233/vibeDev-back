package com.vibedev.dto.mute;

import java.time.Instant;

public record MuteRecordItem(
    String id,
    String userId,
    String moderatorId,
    String duration,
    String reason,
    boolean isActive,
    String unmutedBy,
    Instant unmutedAt,
    Instant createdAt
) {}
