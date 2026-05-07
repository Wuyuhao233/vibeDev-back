package com.vibedev.repository;

import com.vibedev.entity.ModeratorBoard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModeratorBoardRepository extends JpaRepository<ModeratorBoard, String> {

    List<ModeratorBoard> findByUserId(String userId);

    List<ModeratorBoard> findByBoardIdIn(List<String> boardIds);

    void deleteByUserId(String userId);
}
