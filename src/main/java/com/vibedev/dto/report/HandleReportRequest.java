package com.vibedev.dto.report;

public record HandleReportRequest(
    String result,
    String resultDescription,
    String banDuration,
    String banReason
) {}
