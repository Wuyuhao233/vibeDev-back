package com.vibedev.repository;

import com.vibedev.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, String> {

    Optional<Post> findByIdAndIsDeletedFalse(String id);

    Page<Post> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(String authorId, Pageable pageable);

    Page<Post> findByAuthorIdOrderByCreatedAtDesc(String authorId, Pageable pageable);

    int countByTitleAndBoardIdAndIsDeletedFalse(String title, String boardId);

    int countByAuthorIdAndCreatedAtAfter(String authorId, Instant since);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.authorId = :authorId AND p.createdAt >= :since")
    int countByAuthorIdSince(@Param("authorId") String authorId, @Param("since") Instant since);

    // Board posts: default hot sort (by heat_score DESC)
    @Query("SELECT p FROM Post p WHERE p.boardId = :boardId AND p.isDeleted = false AND p.auditStatus = 'approved'" +
            " AND (:tagId IS NULL OR p.id IN (SELECT pt.postId FROM PostTag pt WHERE pt.tagId = :tagId))" +
            " ORDER BY p.heatScore DESC")
    Page<Post> findByBoardIdHot(@Param("boardId") String boardId, @Param("tagId") String tagId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.boardId = :boardId AND p.isDeleted = false AND p.auditStatus = 'approved'" +
            " AND (:tagId IS NULL OR p.id IN (SELECT pt.postId FROM PostTag pt WHERE pt.tagId = :tagId))" +
            " ORDER BY p.createdAt DESC")
    Page<Post> findByBoardIdLatest(@Param("boardId") String boardId, @Param("tagId") String tagId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.boardId = :boardId AND p.isDeleted = false AND p.auditStatus = 'approved'" +
            " AND p.createdAt >= :since" +
            " AND (:tagId IS NULL OR p.id IN (SELECT pt.postId FROM PostTag pt WHERE pt.tagId = :tagId))" +
            " ORDER BY p.heatScore DESC")
    Page<Post> findByBoardIdTrending(@Param("boardId") String boardId, @Param("tagId") String tagId,
                                     @Param("since") Instant since, Pageable pageable);

    // Feed: trending (24h, sorted by heat_score)
    @Query("SELECT p FROM Post p WHERE p.isDeleted = false AND p.auditStatus = 'approved'" +
            " AND p.createdAt >= :since ORDER BY p.heatScore DESC")
    Page<Post> findTrending(@Param("since") Instant since, Pageable pageable);

    // Feed: following (posts in followed tags, ordered by created_at DESC)
    @Query("SELECT p FROM Post p WHERE p.isDeleted = false AND p.auditStatus = 'approved'" +
            " AND p.id IN (SELECT pt.postId FROM PostTag pt WHERE pt.tagId IN :tagIds)" +
            " ORDER BY p.createdAt DESC")
    Page<Post> findByTagIds(@Param("tagIds") List<String> tagIds, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.auditStatus = 'approved' AND p.isDeleted = false AND p.createdAt >= :since")
    List<Post> findApprovedSince(@Param("since") Instant since);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.auditStatus = 'approved' AND p.isDeleted = false AND p.createdAt >= :since")
    long countApprovedSince(@Param("since") Instant since);

    // Admin post list: includes deleted, filterable by board/status/search
    @Query("SELECT p FROM Post p WHERE " +
            "(:boardId IS NULL OR p.boardId = :boardId) AND " +
            "(:status IS NULL OR " +
            "  (:status = 'active' AND p.isDeleted = false) OR " +
            "  (:status = 'deleted' AND p.isDeleted = true)) AND " +
            "(:search IS NULL OR p.title LIKE %:search% OR p.contentMarkdown LIKE %:search%) " +
            "ORDER BY p.createdAt DESC")
    Page<Post> findPostsForAdmin(@Param("boardId") String boardId,
                                  @Param("status") String status,
                                  @Param("search") String search,
                                  Pageable pageable);

    // HotScore update
    @Modifying
    @Query(value = "UPDATE posts SET heat_score = " +
            "(like_count * 1 + reply_count * 2 + collect_count * 3) / " +
            "(1 + TIMESTAMPDIFF(HOUR, created_at, NOW()) * 0.05) " +
            "WHERE created_at > DATE_SUB(NOW(), INTERVAL 7 DAY)", nativeQuery = true)
    int updateHeatScores();
}
