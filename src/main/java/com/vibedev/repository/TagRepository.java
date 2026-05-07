package com.vibedev.repository;

import com.vibedev.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TagRepository extends JpaRepository<Tag, String> {

    List<Tag> findByBoardIdOrderBySortOrder(String boardId);
}
