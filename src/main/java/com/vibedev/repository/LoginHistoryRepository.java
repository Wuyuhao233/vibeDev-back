package com.vibedev.repository;

import com.vibedev.entity.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, String> {

    Page<LoginHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserIdAndCreatedAtAfter(String userId, java.time.Instant since);
}
