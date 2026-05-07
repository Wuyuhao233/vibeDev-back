package com.vibedev.dto.admin.apiusage;

import java.math.BigDecimal;
import java.util.List;

public record ApiUsageResponse(
        long todayCalls,
        long monthlyCalls,
        BigDecimal monthlyCost,
        BigDecimal budgetRemaining,
        boolean isBudgetExceeded,
        List<DailyBreakdown> dailyBreakdown
) {
    public record DailyBreakdown(
            String date,
            long calls,
            BigDecimal cost
    ) {}
}
