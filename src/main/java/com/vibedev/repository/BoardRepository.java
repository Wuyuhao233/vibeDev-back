package com.vibedev.repository;

import com.vibedev.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, String> {

    List<Board> findByStatusAndIsDeletedFalseOrderBySortOrder(String status);

    Optional<Board> findByIdAndIsDeletedFalse(String id);
}
