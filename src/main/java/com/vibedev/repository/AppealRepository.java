package com.vibedev.repository;

import com.vibedev.entity.Appeal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppealRepository extends JpaRepository<Appeal, String> {

    Optional<Appeal> findByReportIdAndStatus(String reportId, String status);

    Page<Appeal> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    long countByStatus(String status);
}
