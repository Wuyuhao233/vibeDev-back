package com.vibedev.dto.moderation;

import java.math.BigDecimal;
import java.util.Map;

public record ReviewStatsResponse(
        QueueStats queue,
        ReportStats reports,
        QualityStats quality,
        CostStats cost
) {
    public record QueueStats(
            long pendingCount,
            long appealCount,
            long todayApproved,
            long todayRejected
    ) {}

    public record ReportStats(
            long pendingCount,
            long todayResolved
    ) {}

    public record QualityStats(
            double passRate,
            double blockRate,
            double manualPassRate,
            double falsePositiveRate,
            double missRate
    ) {}

    public record CostStats(
            BigDecimal monthlyBudget,
            BigDecimal monthlyCost,
            long dailyApiCalls,
            boolean isBudgetExceeded
    ) {}
}
