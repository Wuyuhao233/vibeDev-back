package com.vibedev.repository;

import com.vibedev.entity.UserFollowedTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFollowedTagRepository extends JpaRepository<UserFollowedTag, String> {

    List<UserFollowedTag> findByUserId(String userId);

    Optional<UserFollowedTag> findByUserIdAndTagId(String userId, String tagId);

    boolean existsByUserIdAndTagId(String userId, String tagId);

    void deleteByUserIdAndTagId(String userId, String tagId);
}
