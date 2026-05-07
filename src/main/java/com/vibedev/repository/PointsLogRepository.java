package com.vibedev.repository;

import com.vibedev.entity.PointsLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointsLogRepository extends JpaRepository<PointsLog, String> {

    Page<PointsLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(pl.amount), 0) FROM PointsLog pl WHERE pl.userId = :userId")
    int sumAmountByUserId(@Param("userId") String userId);
}
