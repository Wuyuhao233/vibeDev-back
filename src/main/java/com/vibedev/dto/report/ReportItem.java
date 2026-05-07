package com.vibedev.dto.report;

import java.time.Instant;

public record ReportItem(
    String id,
    String reporterId,
    String targetType,
    String targetId,
    String reasonType,
    String description,
    String status,
    String result,
    String resultDescription,
    String handlerId,
    boolean isMalicious,
    Instant createdAt,
    Instant processedAt
) {}
