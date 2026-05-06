package com.vibedev.repository;

import com.vibedev.entity.Reply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplyRepository extends JpaRepository<Reply, String> {

    Page<Reply> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(String authorId, Pageable pageable);

    Page<Reply> findByAuthorIdOrderByCreatedAtDesc(String authorId, Pageable pageable);
}
