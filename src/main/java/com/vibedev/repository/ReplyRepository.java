package com.vibedev.repository;

import com.vibedev.entity.Reply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface ReplyRepository extends JpaRepository<Reply, String> {

    Optional<Reply> findByIdAndIsDeletedFalse(String id);

    Page<Reply> findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(String postId, Pageable pageable);

    Page<Reply> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(String authorId, Pageable pageable);

    Page<Reply> findByAuthorIdOrderByCreatedAtDesc(String authorId, Pageable pageable);

    int countByAuthorIdAndPostIdAndCreatedAtAfter(String authorId, String postId, Instant since);

    int countByAuthorIdAndCreatedAtAfter(String authorId, Instant since);
}
