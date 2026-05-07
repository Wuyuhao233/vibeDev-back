package com.vibedev.dto.report;

import java.time.Instant;

public record CheckBanResponse(
    boolean isBannedFromReporting,
    Instant bannedUntil,
    int maliciousCount
) {}
