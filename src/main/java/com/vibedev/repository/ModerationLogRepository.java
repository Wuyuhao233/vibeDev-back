package com.vibedev.repository;

import com.vibedev.entity.ModerationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ModerationLogRepository extends JpaRepository<ModerationLog, String> {

    List<ModerationLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, String targetId);

    @Query("SELECT COUNT(m) FROM ModerationLog m WHERE m.action = :action AND m.createdAt >= :since")
    long countByActionSince(@Param("action") String action, @Param("since") Instant since);

    @Query("SELECT m.operatorId, COUNT(m) FROM ModerationLog m WHERE m.operatorId IS NOT NULL AND m.createdAt >= :since GROUP BY m.operatorId")
    List<Object[]> countByOperatorSince(@Param("since") Instant since);
}
