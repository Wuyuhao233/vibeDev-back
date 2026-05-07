package com.vibedev.dto.report;

public record CreateReportRequest(
    String targetType,
    String targetId,
    String reasonType,
    String description
) {}
