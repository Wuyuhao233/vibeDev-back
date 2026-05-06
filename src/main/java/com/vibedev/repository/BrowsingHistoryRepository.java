package com.vibedev.repository;

import com.vibedev.entity.BrowsingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface BrowsingHistoryRepository extends JpaRepository<BrowsingHistory, String> {

    Page<BrowsingHistory> findByUserIdOrderByViewedAtDesc(String userId, Pageable pageable);

    long countByUserId(String userId);

    @Modifying
    @Query("DELETE FROM BrowsingHistory bh WHERE bh.userId = :userId AND bh.viewedAt < :cutoff")
    int deleteOldRecords(String userId, Instant cutoff);

    @Query("SELECT bh FROM BrowsingHistory bh WHERE bh.userId = :userId AND bh.postId = :postId")
    Optional<BrowsingHistory> findByUserIdAndPostId(String userId, String postId);

    @Modifying
    @Query("DELETE FROM BrowsingHistory bh WHERE bh.userId = :userId AND bh.id NOT IN " +
           "(SELECT b.id FROM BrowsingHistory b WHERE b.userId = :userId ORDER BY b.viewedAt DESC)")
    int deleteExcessRecords(String userId, int maxRecords);
}
