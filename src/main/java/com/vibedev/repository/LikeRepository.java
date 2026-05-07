package com.vibedev.repository;

import com.vibedev.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, String> {

    Optional<Like> findByUserIdAndTargetTypeAndTargetId(String userId, String targetType, String targetId);

    List<Like> findByUserIdAndTargetTypeAndTargetIdIn(String userId, String targetType, List<String> targetIds);

    int countByTargetTypeAndTargetId(String targetType, String targetId);

    boolean existsByUserIdAndTargetTypeAndTargetId(String userId, String targetType, String targetId);

    void deleteByUserIdAndTargetTypeAndTargetId(String userId, String targetType, String targetId);
}
