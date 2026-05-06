package com.vibedev.dto.user;

import java.time.Instant;

public record LoginRecord(
        String id,
        String loginType,
        String ipAddress,
        String userAgent,
        boolean success,
        Instant createdAt
) {}
