package com.vibedev.repository;

import com.vibedev.entity.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, String> {

    long countByCreatedAtAfter(Instant since);

    @Query("SELECT COALESCE(SUM(a.cost), 0) FROM AiAuditLog a WHERE a.createdAt >= :since")
    BigDecimal sumCostSince(@Param("since") Instant since);
}
