package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.entity.User;
import com.vibedev.entity.UserFollow;
import com.vibedev.repository.UserFollowRepository;
import com.vibedev.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FollowService {

    private final UserFollowRepository userFollowRepo;
    private final UserRepository userRepo;

    public FollowService(UserFollowRepository userFollowRepo, UserRepository userRepo) {
        this.userFollowRepo = userFollowRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public void followUser(String userId, String targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.VALIDATION_TAGS, "不能关注自己");
        }
        if (!userRepo.existsById(targetUserId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (userFollowRepo.existsByUserIdAndFollowedUserId(userId, targetUserId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "已关注该用户");
        }
        var uf = new UserFollow(UUID.randomUUID().toString(), userId, targetUserId);
        userFollowRepo.save(uf);
    }

    @Transactional
    public void unfollowUser(String userId, String targetUserId) {
        var uf = userFollowRepo.findByUserIdAndFollowedUserId(userId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "未关注该用户"));
        userFollowRepo.delete(uf);
    }

    public boolean isFollowing(String userId, String targetUserId) {
        return userFollowRepo.existsByUserIdAndFollowedUserId(userId, targetUserId);
    }

    /** Get IDs of users that the given user follows */
    public List<String> getFollowingIds(String userId) {
        return userFollowRepo.findByUserId(userId).stream()
                .map(UserFollow::getFollowedUserId)
                .toList();
    }

    public long getFollowingCount(String userId) {
        return userFollowRepo.countByUserId(userId);
    }

    public long getFollowerCount(String userId) {
        return userFollowRepo.countByFollowedUserId(userId);
    }
}
