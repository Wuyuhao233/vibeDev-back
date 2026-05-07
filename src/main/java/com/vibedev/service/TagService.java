package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.entity.UserFollowedTag;
import com.vibedev.repository.TagRepository;
import com.vibedev.repository.UserFollowedTagRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TagService {

    private static final String USER_PROFILE_CACHE_PREFIX = "user_profile:";

    private final TagRepository tagRepo;
    private final UserFollowedTagRepository userFollowedTagRepo;
    private final StringRedisTemplate redis;

    public TagService(TagRepository tagRepo, UserFollowedTagRepository userFollowedTagRepo,
                      StringRedisTemplate redis) {
        this.tagRepo = tagRepo;
        this.userFollowedTagRepo = userFollowedTagRepo;
        this.redis = redis;
    }

    @Transactional
    public void followTag(String userId, String tagId) {
        if (!tagRepo.existsById(tagId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        if (userFollowedTagRepo.existsByUserIdAndTagId(userId, tagId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "已关注该标签");
        }
        var uft = new UserFollowedTag(UUID.randomUUID().toString(), userId, tagId);
        userFollowedTagRepo.save(uft);
        clearUserProfileCache(userId);
    }

    @Transactional
    public void unfollowTag(String userId, String tagId) {
        var uft = userFollowedTagRepo.findByUserIdAndTagId(userId, tagId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "未关注该标签"));
        userFollowedTagRepo.delete(uft);
        clearUserProfileCache(userId);
    }

    private void clearUserProfileCache(String userId) {
        try {
            redis.delete(USER_PROFILE_CACHE_PREFIX + userId + ":tags");
        } catch (Exception ignored) {
            // Redis unavailable, cache will expire naturally
        }
    }
}
