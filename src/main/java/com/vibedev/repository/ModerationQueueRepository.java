package com.vibedev.repository;

import com.vibedev.entity.ModerationQueue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ModerationQueueRepository extends JpaRepository<ModerationQueue, String> {

    Page<ModerationQueue> findByStatusOrderByPriorityDescCreatedAtAsc(String status, Pageable pageable);

    Page<ModerationQueue> findByStatusAndBoardIdInOrderByPriorityDescCreatedAtAsc(
            String status, List<String> boardIds, Pageable pageable);

    Page<ModerationQueue> findByStatusAndTargetTypeOrderByPriorityDescCreatedAtAsc(
            String status, String targetType, Pageable pageable);

    Page<ModerationQueue> findByStatusAndTargetTypeAndBoardIdInOrderByPriorityDescCreatedAtAsc(
            String status, String targetType, List<String> boardIds, Pageable pageable);

    // ─── "All" status queries (no status filter) ──────

    Page<ModerationQueue> findAllByOrderByPriorityDescCreatedAtAsc(Pageable pageable);

    Page<ModerationQueue> findByBoardIdInOrderByPriorityDescCreatedAtAsc(
            List<String> boardIds, Pageable pageable);

    Page<ModerationQueue> findByTargetTypeOrderByPriorityDescCreatedAtAsc(
            String targetType, Pageable pageable);

    Page<ModerationQueue> findByTargetTypeAndBoardIdInOrderByPriorityDescCreatedAtAsc(
            String targetType, List<String> boardIds, Pageable pageable);

    Optional<ModerationQueue> findByTargetTypeAndTargetIdAndStatus(String targetType, String targetId, String status);

    Optional<ModerationQueue> findFirstByTargetTypeAndTargetIdAndAiScoreGreaterThanOrderByAiScoreDesc(
            String targetType, String targetId, int aiScore);

    @Query("SELECT COUNT(m) FROM ModerationQueue m WHERE m.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(m) FROM ModerationQueue m WHERE m.status = 'approved' AND m.handledAt >= :since")
    long countApprovedSince(@Param("since") Instant since);

    @Query("SELECT COUNT(m) FROM ModerationQueue m WHERE m.status = 'rejected' AND m.handledAt >= :since")
    long countRejectedSince(@Param("since") Instant since);

    @Query("SELECT m.boardId, COUNT(m) FROM ModerationQueue m WHERE m.createdAt >= :since GROUP BY m.boardId")
    List<Object[]> countByBoardSince(@Param("since") Instant since);

    @Query("SELECT COUNT(m) FROM ModerationQueue m WHERE m.aiScore >= 85 AND m.createdAt >= :since")
    long countAutoRejectedSince(@Param("since") Instant since);

    @Query("SELECT COUNT(m) FROM ModerationQueue m WHERE m.aiScore < 50 AND m.createdAt >= :since")
    long countAutoApprovedSince(@Param("since") Instant since);
}
