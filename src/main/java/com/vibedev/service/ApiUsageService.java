package com.vibedev.service;

import com.vibedev.dto.admin.apiusage.ApiUsageResponse;
import com.vibedev.repository.AiAuditLogRepository;
import com.vibedev.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class ApiUsageService {

    private static final BigDecimal MONTHLY_BUDGET_DEFAULT = new BigDecimal("200");
    private static final BigDecimal USD_PER_TOKEN = new BigDecimal("0.000002");

    private final AiAuditLogRepository aiAuditLogRepo;
    private final SystemConfigRepository systemConfigRepo;

    public ApiUsageService(AiAuditLogRepository aiAuditLogRepo,
                           SystemConfigRepository systemConfigRepo) {
        this.aiAuditLogRepo = aiAuditLogRepo;
        this.systemConfigRepo = systemConfigRepo;
    }

    public ApiUsageResponse getApiUsage(int days) {
        Instant todayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant since = Instant.now().minus(days, java.time.temporal.ChronoUnit.DAYS).truncatedTo(java.time.temporal.ChronoUnit.DAYS);

        // Today's calls
        long todayCalls = aiAuditLogRepo.countByCreatedAtAfter(todayStart);

        // Monthly stats
        var monthStats = aiAuditLogRepo.countAndCostSince(monthStart);
        long monthlyCalls = 0;
        BigDecimal monthlyCost = BigDecimal.ZERO;
        if (!monthStats.isEmpty() && monthStats.get(0)[0] != null) {
            monthlyCalls = (Long) monthStats.get(0)[0];
        }
        if (!monthStats.isEmpty() && monthStats.get(0)[1] != null) {
            monthlyCost = (BigDecimal) monthStats.get(0)[1];
        }

        // Budget
        BigDecimal monthlyBudget = getMonthlyBudget();
        BigDecimal budgetRemaining = monthlyBudget.subtract(monthlyCost);
        boolean budgetExceeded = budgetRemaining.compareTo(BigDecimal.ZERO) < 0;

        // Daily breakdown
        var rawBreakdown = aiAuditLogRepo.dailyBreakdownSince(since);
        List<ApiUsageResponse.DailyBreakdown> dailyBreakdown = new ArrayList<>();
        for (Object[] row : rawBreakdown) {
            String date = row[0].toString();
            long calls = (Long) row[1];
            BigDecimal cost = (BigDecimal) row[2];
            dailyBreakdown.add(new ApiUsageResponse.DailyBreakdown(date, calls, cost));
        }

        return new ApiUsageResponse(
                todayCalls,
                monthlyCalls,
                monthlyCost,
                budgetRemaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : budgetRemaining,
                budgetExceeded,
                dailyBreakdown
        );
    }

    private BigDecimal getMonthlyBudget() {
        return systemConfigRepo.findByConfigKey("monthly_budget_cap")
                .map(config -> {
                    try {
                        return new BigDecimal(config.getConfigValue());
                    } catch (NumberFormatException e) {
                        return MONTHLY_BUDGET_DEFAULT;
                    }
                })
                .orElse(MONTHLY_BUDGET_DEFAULT);
    }
}
