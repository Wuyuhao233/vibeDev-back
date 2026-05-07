package com.vibedev.repository;

import com.vibedev.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, String> {

    Optional<Report> findByReporterIdAndTargetTypeAndTargetIdAndStatus(String reporterId, String targetType, String targetId, String status);

    Page<Report> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<Report> findByStatusAndTargetTypeOrderByCreatedAtDesc(String status, String targetType, Pageable pageable);

    @Query("SELECT r FROM Report r WHERE r.status = :status AND r.targetId IN " +
           "(SELECT p.id FROM Post p WHERE p.boardId = :boardId)")
    Page<Report> findByStatusAndBoardId(String status, String boardId, Pageable pageable);

    long countByStatus(String status);

    long countByReporterIdAndIsMaliciousTrue(String reporterId);

    List<Report> findByTargetTypeAndTargetIdAndStatus(String targetType, String targetId, String status);
}
