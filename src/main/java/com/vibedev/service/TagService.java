package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.entity.UserFollowedTag;
import com.vibedev.repository.TagRepository;
import com.vibedev.repository.UserFollowedTagRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.vibedev.dto.board.TagItem;
import com.vibedev.entity.Tag;

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

    public List<TagItem> getAllTags() {
        return tagRepo.findAll().stream()
                .map(tag -> new TagItem(tag.getId(), tag.getName(), tag.getBoardId(), tag.getSortOrder(), tag.getPostCount()))
                .collect(Collectors.toList());
    }

    public List<TagItem> getFollowedTags(String userId) {
        return userFollowedTagRepo.findByUserId(userId).stream()
                .map(uft -> tagRepo.findById(uft.getTagId())
                        .map(tag -> new TagItem(tag.getId(), tag.getName(), tag.getBoardId(), tag.getSortOrder(), tag.getPostCount()))
                        .orElse(null))
                .filter(tag -> tag != null)
                .collect(Collectors.toList());
    }

    /**
     * Find existing tag by board+name, or create a new one if not found.
     * Auto-assigns sortOrder = max existing + 1.
     */
    @Transactional
    public Tag findOrCreateTag(String boardId, String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 20) {
            throw new BusinessException(ErrorCode.VALIDATION_TAGS, "标签名需1-20个字符");
        }
        return tagRepo.findByBoardIdAndName(boardId, trimmed)
                .orElseGet(() -> {
                    Tag newTag = new Tag();
                    newTag.setId(UUID.randomUUID().toString());
                    newTag.setBoardId(boardId);
                    newTag.setName(trimmed);
                    // assign next sort order
                    var existing = tagRepo.findByBoardIdOrderBySortOrder(boardId);
                    int nextOrder = existing.isEmpty() ? 0 :
                            existing.stream().mapToInt(Tag::getSortOrder).max().orElse(0) + 1;
                    newTag.setSortOrder(nextOrder);
                    newTag.setPostCount(0);
                    return tagRepo.save(newTag);
                });
    }

    private void clearUserProfileCache(String userId) {
        try {
            redis.delete(USER_PROFILE_CACHE_PREFIX + userId + ":tags");
        } catch (Exception ignored) {
            // Redis unavailable, cache will expire naturally
        }
    }
}
