package com.vibedev.dto.user;

import java.time.Instant;

public record ExportDataResponse(
        String taskId,
        String status,
        String downloadUrl,
        Instant expiresAt,
        int progress,
        Instant requestedAt
) {}
