package com.vibedev.repository;

import com.vibedev.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, String> {

    List<Tag> findByBoardIdOrderBySortOrder(String boardId);

    Optional<Tag> findByBoardIdAndName(String boardId, String name);
}
