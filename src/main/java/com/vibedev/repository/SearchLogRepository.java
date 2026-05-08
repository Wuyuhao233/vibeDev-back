package com.vibedev.repository;

import com.vibedev.entity.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    @Query("SELECT s.keyword, COUNT(s) as cnt FROM SearchLog s " +
           "WHERE s.createdAt >= :since " +
           "GROUP BY s.keyword ORDER BY cnt DESC")
    List<Object[]> findHotKeywords(@Param("since") Instant since);
}
