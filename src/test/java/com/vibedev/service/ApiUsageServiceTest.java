package com.vibedev.service;

import com.vibedev.entity.SystemConfig;
import com.vibedev.repository.AiAuditLogRepository;
import com.vibedev.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiUsageServiceTest {

    @Mock AiAuditLogRepository aiAuditLogRepo;
    @Mock SystemConfigRepository systemConfigRepo;

    @InjectMocks ApiUsageService apiUsageService;

    @SuppressWarnings("unchecked")
    private List<Object[]> mockList(Object[]... rows) {
        return List.of(rows);
    }

    @Test
    void getApiUsage_shouldReturnUsageStats() {
        when(aiAuditLogRepo.countByCreatedAtAfter(any())).thenReturn(50L);
        when(aiAuditLogRepo.countAndCostSince(any()))
                .thenReturn(mockList(new Object[]{200L, BigDecimal.valueOf(15.6)}));
        when(aiAuditLogRepo.dailyBreakdownSince(any()))
                .thenReturn(mockList(
                        new Object[]{"2026-05-06", 30L, BigDecimal.valueOf(2.5)},
                        new Object[]{"2026-05-07", 20L, BigDecimal.valueOf(1.2)}
                ));
        when(systemConfigRepo.findByConfigKey("monthly_budget_cap")).thenReturn(Optional.empty());

        var result = apiUsageService.getApiUsage(30);

        assertEquals(50, result.todayCalls());
        assertEquals(200, result.monthlyCalls());
        assertEquals(BigDecimal.valueOf(15.6), result.monthlyCost());
        assertEquals(new BigDecimal("184.4"), result.budgetRemaining());
        assertFalse(result.isBudgetExceeded());
        assertEquals(2, result.dailyBreakdown().size());
        assertEquals("2026-05-06", result.dailyBreakdown().get(0).date());
        assertEquals(30, result.dailyBreakdown().get(0).calls());
    }

    @Test
    void getApiUsage_shouldDetectBudgetExceeded() {
        when(aiAuditLogRepo.countByCreatedAtAfter(any())).thenReturn(300L);
        when(aiAuditLogRepo.countAndCostSince(any()))
                .thenReturn(mockList(new Object[]{8000L, BigDecimal.valueOf(250.0)}));
        when(aiAuditLogRepo.dailyBreakdownSince(any())).thenReturn(Collections.emptyList());
        when(systemConfigRepo.findByConfigKey("monthly_budget_cap")).thenReturn(Optional.empty());

        var result = apiUsageService.getApiUsage(30);

        assertTrue(result.isBudgetExceeded());
        assertEquals(BigDecimal.ZERO, result.budgetRemaining());
    }

    @Test
    void getApiUsage_shouldUseCustomBudgetFromConfig() {
        var config = new SystemConfig();
        config.setConfigKey("monthly_budget_cap");
        config.setConfigValue("500");

        when(aiAuditLogRepo.countByCreatedAtAfter(any())).thenReturn(10L);
        when(aiAuditLogRepo.countAndCostSince(any()))
                .thenReturn(mockList(new Object[]{100L, BigDecimal.valueOf(50.0)}));
        when(aiAuditLogRepo.dailyBreakdownSince(any())).thenReturn(Collections.emptyList());
        when(systemConfigRepo.findByConfigKey("monthly_budget_cap"))
                .thenReturn(Optional.of(config));

        var result = apiUsageService.getApiUsage(30);

        assertEquals(new BigDecimal("450.0"), result.budgetRemaining());
        assertFalse(result.isBudgetExceeded());
    }

    @Test
    void getApiUsage_shouldHandleEmptyData() {
        when(aiAuditLogRepo.countByCreatedAtAfter(any())).thenReturn(0L);
        when(aiAuditLogRepo.countAndCostSince(any())).thenReturn(Collections.emptyList());
        when(aiAuditLogRepo.dailyBreakdownSince(any())).thenReturn(Collections.emptyList());
        when(systemConfigRepo.findByConfigKey("monthly_budget_cap")).thenReturn(Optional.empty());

        var result = apiUsageService.getApiUsage(30);

        assertEquals(0, result.todayCalls());
        assertEquals(0, result.monthlyCalls());
        assertEquals(BigDecimal.ZERO, result.monthlyCost());
        assertEquals(new BigDecimal("200"), result.budgetRemaining());
        assertTrue(result.dailyBreakdown().isEmpty());
    }

    @Test
    void getApiUsage_shouldHandleInvalidBudgetConfig() {
        var config = new SystemConfig();
        config.setConfigKey("monthly_budget_cap");
        config.setConfigValue("invalid");

        when(aiAuditLogRepo.countByCreatedAtAfter(any())).thenReturn(0L);
        when(aiAuditLogRepo.countAndCostSince(any()))
                .thenReturn(mockList(new Object[]{0L, BigDecimal.ZERO}));
        when(aiAuditLogRepo.dailyBreakdownSince(any())).thenReturn(Collections.emptyList());
        when(systemConfigRepo.findByConfigKey("monthly_budget_cap"))
                .thenReturn(Optional.of(config));

        var result = apiUsageService.getApiUsage(30);
        assertEquals(new BigDecimal("200"), result.budgetRemaining());
    }
}
