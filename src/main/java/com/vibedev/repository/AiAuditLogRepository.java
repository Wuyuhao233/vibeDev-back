package com.vibedev.repository;

import com.vibedev.entity.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, String> {

    long countByCreatedAtAfter(Instant since);

    @Query("SELECT COALESCE(SUM(a.cost), 0) FROM AiAuditLog a WHERE a.createdAt >= :since")
    BigDecimal sumCostSince(@Param("since") Instant since);

    @Query("SELECT COALESCE(AVG(a.responseTimeMs), 0) FROM AiAuditLog a WHERE a.createdAt >= :since")
    double avgResponseTimeMsSince(@Param("since") Instant since);

    @Query("SELECT COUNT(a), COALESCE(SUM(a.cost), 0) FROM AiAuditLog a WHERE a.createdAt >= :since")
    List<Object[]> countAndCostSince(@Param("since") Instant since);

    @Query("SELECT FUNCTION('DATE', a.createdAt), COUNT(a), COALESCE(SUM(a.cost), 0) " +
           "FROM AiAuditLog a WHERE a.createdAt >= :since " +
           "GROUP BY FUNCTION('DATE', a.createdAt) ORDER BY FUNCTION('DATE', a.createdAt)")
    List<Object[]> dailyBreakdownSince(@Param("since") Instant since);
}
