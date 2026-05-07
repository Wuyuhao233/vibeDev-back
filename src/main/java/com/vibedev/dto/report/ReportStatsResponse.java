package com.vibedev.dto.report;

public record ReportStatsResponse(
    long pendingCount,
    long processedToday,
    long totalReports
) {}
