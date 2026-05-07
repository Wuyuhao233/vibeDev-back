package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.entity.UserFollowedTag;
import com.vibedev.repository.TagRepository;
import com.vibedev.repository.UserFollowedTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagServiceTest {

    @Mock TagRepository tagRepo;
    @Mock UserFollowedTagRepository userFollowedTagRepo;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagService(tagRepo, userFollowedTagRepo, redis);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ─── followTag ─────────────────────────────────────────

    @Test
    void followTag_shouldCreateFollow() {
        when(tagRepo.existsById("t1")).thenReturn(true);
        when(userFollowedTagRepo.existsByUserIdAndTagId("u1", "t1")).thenReturn(false);

        tagService.followTag("u1", "t1");

        verify(userFollowedTagRepo).save(any(UserFollowedTag.class));
        verify(redis).delete("user_profile:u1:tags");
    }

    @Test
    void followTag_shouldThrowWhenTagNotFound() {
        when(tagRepo.existsById("t99")).thenReturn(false);

        var ex = assertThrows(BusinessException.class,
                () -> tagService.followTag("u1", "t99"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("标签不存在", ex.getMessage());
    }

    @Test
    void followTag_shouldThrowWhenAlreadyFollowing() {
        when(tagRepo.existsById("t1")).thenReturn(true);
        when(userFollowedTagRepo.existsByUserIdAndTagId("u1", "t1")).thenReturn(true);

        var ex = assertThrows(BusinessException.class,
                () -> tagService.followTag("u1", "t1"));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
        assertEquals("已关注该标签", ex.getMessage());
    }

    // ─── unfollowTag ───────────────────────────────────────

    @Test
    void unfollowTag_shouldDeleteFollow() {
        var uft = new UserFollowedTag("id1", "u1", "t1");
        when(userFollowedTagRepo.findByUserIdAndTagId("u1", "t1")).thenReturn(Optional.of(uft));

        tagService.unfollowTag("u1", "t1");

        verify(userFollowedTagRepo).delete(uft);
        verify(redis).delete("user_profile:u1:tags");
    }

    @Test
    void unfollowTag_shouldThrowWhenNotFollowing() {
        when(userFollowedTagRepo.findByUserIdAndTagId("u1", "t1")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> tagService.unfollowTag("u1", "t1"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("未关注该标签", ex.getMessage());
    }

    // ─── Redis resilience ──────────────────────────────────

    @Test
    void followTag_shouldNotFailWhenRedisUnavailable() {
        when(tagRepo.existsById("t1")).thenReturn(true);
        when(userFollowedTagRepo.existsByUserIdAndTagId("u1", "t1")).thenReturn(false);
        when(redis.delete(anyString())).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> tagService.followTag("u1", "t1"));
        verify(userFollowedTagRepo).save(any(UserFollowedTag.class));
    }

    @Test
    void unfollowTag_shouldNotFailWhenRedisUnavailable() {
        var uft = new UserFollowedTag("id1", "u1", "t1");
        when(userFollowedTagRepo.findByUserIdAndTagId("u1", "t1")).thenReturn(Optional.of(uft));
        when(redis.delete(anyString())).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> tagService.unfollowTag("u1", "t1"));
        verify(userFollowedTagRepo).delete(uft);
    }
}
