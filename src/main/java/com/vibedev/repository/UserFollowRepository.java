package com.vibedev.repository;

import com.vibedev.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, String> {

    List<UserFollow> findByUserId(String userId);

    List<UserFollow> findByFollowedUserId(String followedUserId);

    Optional<UserFollow> findByUserIdAndFollowedUserId(String userId, String followedUserId);

    boolean existsByUserIdAndFollowedUserId(String userId, String followedUserId);

    long countByUserId(String userId);

    long countByFollowedUserId(String followedUserId);

    void deleteByUserIdAndFollowedUserId(String userId, String followedUserId);
}
