package com.vibedev.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.board.PostCard;
import com.vibedev.dto.board.PostCardAuthor;
import com.vibedev.dto.board.TagItem;
import com.vibedev.entity.*;
import com.vibedev.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedServiceTest {

    @Mock PostRepository postRepo;
    @Mock PostTagRepository postTagRepo;
    @Mock TagRepository tagRepo;
    @Mock UserRepository userRepo;
    @Mock BoardRepository boardRepo;
    @Mock BoardService boardService;
    @Mock UserFollowedTagRepository userFollowedTagRepo;
    @Mock BrowsingHistoryRepository browsingHistoryRepo;
    @Mock SystemConfigRepository systemConfigRepo;
    @Mock FollowService followService;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    FeedService feedService;
    final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        feedService = new FeedService(postRepo, postTagRepo, tagRepo,
                userRepo, boardRepo, boardService, userFollowedTagRepo,
                browsingHistoryRepo, systemConfigRepo, followService, redis, objectMapper);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(systemConfigRepo.findByConfigKey(anyString())).thenReturn(Optional.empty());
    }

    private Post createPost(String id, String title, String authorId, String boardId) {
        var p = new Post();
        p.setId(id);
        p.setTitle(title);
        p.setContentMarkdown("content for " + title);
        p.setAuthorId(authorId);
        p.setBoardId(boardId);
        p.setCreatedAt(Instant.now());
        p.setHeatScore(10);
        return p;
    }

    private User createUser(String id, String username, int level) {
        var u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setNickname(username);
        u.setAvatarUrl("/avatar.png");
        u.setLevel(level);
        return u;
    }

    private PostCard createPostCard(Post p, User author, String boardName) {
        var authorDto = new PostCardAuthor(
                author.getUsername(), author.getNickname(),
                author.getAvatarUrl(), author.getLevel());
        return new PostCard(p.getId(), p.getTitle(), "summary",
                p.getCoverImageUrl(), authorDto, p.getCreatedAt(),
                p.getLikeCount(), p.getReplyCount(), p.getCollectCount(),
                Collections.emptyList(), p.isPinned(), p.isEssence(), boardName);
    }

    // ─── feedTrending ─────────────────────────────────────

    @Test
    void feedTrending_shouldReturnPagedPosts() {
        var post = createPost("p1", "Trending Post", "u1", "b1");
        var author = createUser("u1", "alice", 3);
        var board = new Board(); board.setId("b1"); board.setName("TestBoard");

        when(postRepo.findTrending(any(), any())).thenReturn(new PageImpl<>(List.of(post)));
        when(postTagRepo.findByPostIdIn(List.of("p1"))).thenReturn(List.of());
        when(userRepo.findAllById(List.of("u1"))).thenReturn(List.of(author));
        when(boardRepo.findAllById(List.of("b1"))).thenReturn(List.of(board));
        when(boardService.buildTagsByPostId(anyList())).thenReturn(Collections.emptyMap());
        when(boardService.toPostCard(any(), any(), any(), anyString()))
                .thenReturn(createPostCard(post, author, "TestBoard"));

        var result = feedService.feedTrending(1, 20);
        assertNotNull(result);
        assertEquals(1, result.items().size());
        verify(postRepo).findTrending(any(), any());
    }

    @Test
    void feedTrending_shouldCapLimitAt50() {
        when(postRepo.findTrending(any(), any())).thenReturn(new PageImpl<>(List.of()));
        var result = feedService.feedTrending(1, 100);
        assertNotNull(result);
    }

    @Test
    void feedTrending_shouldReturnEmptyForNoPosts() {
        when(postRepo.findTrending(any(), any())).thenReturn(new PageImpl<>(List.of()));
        var result = feedService.feedTrending(1, 20);
        assertEquals(0, result.items().size());
        assertEquals(0, result.total());
    }

    // ─── feedRecommend ─────────────────────────────────────

    @Test
    void feedRecommend_shouldDegradeToTrendingWhenUnauthenticated() {
        var post = createPost("p1", "Rec Post", "u1", "b1");
        when(postRepo.findTrending(any(), any())).thenReturn(new PageImpl<>(List.of(post)));
        when(postTagRepo.findByPostIdIn(List.of("p1"))).thenReturn(List.of());
        when(userRepo.findAllById(List.of("u1"))).thenReturn(List.of());
        when(boardRepo.findAllById(List.of("b1"))).thenReturn(List.of());
        when(boardService.buildTagsByPostId(anyList())).thenReturn(Collections.emptyMap());
        when(boardService.toPostCard(any(), any(), any(), anyString()))
                .thenReturn(createPostCard(post, createUser("u1", "alice", 1), "Board"));

        var result = feedService.feedRecommend(null, 1, 20);
        assertEquals(1, result.items().size());
    }

    @Test
    void feedRecommend_shouldDegradeToTrendingForColdStart() {
        var coldUser = mock(User.class);
        when(coldUser.getId()).thenReturn("u1");
        when(coldUser.getCreatedAt()).thenReturn(Instant.now().minusSeconds(86400));
        when(userRepo.findById("u1")).thenReturn(Optional.of(coldUser));
        when(browsingHistoryRepo.countByUserId("u1")).thenReturn(0L);

        var post = createPost("p1", "Hot Post", "u2", "b1");
        when(postRepo.findTrending(any(), any())).thenReturn(new PageImpl<>(List.of(post)));
        when(postTagRepo.findByPostIdIn(List.of("p1"))).thenReturn(List.of());
        when(userRepo.findAllById(List.of("u2"))).thenReturn(List.of());
        when(boardRepo.findAllById(List.of("b1"))).thenReturn(List.of());
        when(boardService.buildTagsByPostId(anyList())).thenReturn(Collections.emptyMap());
        when(boardService.toPostCard(any(), any(), any(), anyString()))
                .thenReturn(createPostCard(post, createUser("u2", "bob", 1), "Board"));

        var result = feedService.feedRecommend("u1", 1, 20);
        assertEquals(1, result.items().size());
        assertTrue(feedService.isColdStart("u1"));
    }

    @Test
    void feedRecommend_shouldUseRuleEngineForWarmUser() {
        var warmUser = mock(User.class);
        when(warmUser.getId()).thenReturn("u1");
        when(warmUser.getCreatedAt()).thenReturn(Instant.now().minusSeconds(30 * 86400));
        when(userRepo.findById("u1")).thenReturn(Optional.of(warmUser));
        when(browsingHistoryRepo.countByUserId("u1")).thenReturn(10L);
        assertFalse(feedService.isColdStart("u1"));

        var post = createPost("p1", "Good Post", "u2", "b1");
        post.setHeatScore(50);
        post.setContentLength(300);
        post.setHasCodeBlock(true);
        post.setCoverImageUrl("/img.png");
        when(postRepo.findTrending(any(), any())).thenReturn(new PageImpl<>(List.of(post)));

        when(userFollowedTagRepo.findByUserId("u1")).thenReturn(List.of());
        when(browsingHistoryRepo.findPostIdsByUserIdAndViewedAtAfter(eq("u1"), any())).thenReturn(List.of());
        when(postRepo.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(eq("u1"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        when(postTagRepo.findByPostIdIn(List.of("p1"))).thenReturn(List.of());
        when(postTagRepo.findByPostIdIn(anyList())).thenReturn(List.of());

        var author = createUser("u2", "author2", 5);
        when(userRepo.findAllById(List.of("u2"))).thenReturn(List.of(author));
        when(userRepo.findAllById(anyList())).thenReturn(List.of(author));

        var board = new Board(); board.setId("b1"); board.setName("TestBoard");
        when(boardRepo.findAllById(List.of("b1"))).thenReturn(List.of(board));
        when(boardRepo.findAllById(anyList())).thenReturn(List.of(board));

        when(boardService.buildTagsByPostId(anyList())).thenReturn(Collections.emptyMap());
        when(boardService.toPostCard(any(), any(), any(), anyString()))
                .thenReturn(createPostCard(post, author, "TestBoard"));

        // Cache miss
        when(valueOps.get(contains("feed:recommend:u1:1"))).thenReturn(null);
        when(valueOps.get(contains("user_profile:u1"))).thenReturn(null);

        var result = feedService.feedRecommend("u1", 1, 20);
        assertNotNull(result);
        assertEquals(1, result.items().size());
        assertEquals("Good Post", result.items().get(0).title());
    }

    // ─── feedFollowing ─────────────────────────────────────

    @Test
    void feedFollowing_shouldReturnEmptyWhenUnauthenticated() {
        var result = feedService.feedFollowing(null, 1, 20);
        assertEquals(0, result.items().size());
        assertEquals(0, result.total());
    }

    @Test
    void feedFollowing_shouldReturnEmptyWhenNoFollowedUsers() {
        when(followService.getFollowingIds("u1")).thenReturn(List.of());
        var result = feedService.feedFollowing("u1", 1, 20);
        assertEquals(0, result.items().size());
        assertEquals(0, result.total());
    }

    @Test
    void feedFollowing_shouldReturnPostsFromFollowedUsers() {
        when(followService.getFollowingIds("u1")).thenReturn(List.of("u2"));

        var post = createPost("p1", "Followed Post", "u2", "b1");
        when(postRepo.findByAuthorIdIn(eq(List.of("u2")), any())).thenReturn(new PageImpl<>(List.of(post)));

        when(postTagRepo.findByPostIdIn(List.of("p1"))).thenReturn(List.of());
        when(postTagRepo.findByPostIdIn(anyList())).thenReturn(List.of());
        when(userRepo.findAllById(List.of("u2"))).thenReturn(List.of());
        when(userRepo.findAllById(anyList())).thenReturn(List.of());
        when(boardRepo.findAllById(List.of("b1"))).thenReturn(List.of());
        when(boardRepo.findAllById(anyList())).thenReturn(List.of());
        when(boardService.buildTagsByPostId(anyList())).thenReturn(Collections.emptyMap());
        when(boardService.toPostCard(any(), any(), any(), anyString()))
                .thenReturn(createPostCard(post, createUser("u2", "bob", 1), "Board"));

        var result = feedService.feedFollowing("u1", 1, 20);
        assertEquals(1, result.items().size());
        verify(postRepo).findByAuthorIdIn(eq(List.of("u2")), any());
    }

    // ─── feedRecommend quality scoring ─────────────────────

    @Test
    void feedRecommend_shouldRankEssencePostsFirst() {
        var warmUser = mock(User.class);
        when(warmUser.getId()).thenReturn("u1");
        when(warmUser.getCreatedAt()).thenReturn(Instant.now().minusSeconds(30 * 86400));
        when(userRepo.findById("u1")).thenReturn(Optional.of(warmUser));
        when(browsingHistoryRepo.countByUserId("u1")).thenReturn(5L);

        var post1 = createPost("p1", "Regular", "u2", "b1");
        post1.setHeatScore(10);
        var post2 = createPost("p2", "Essence", "u3", "b1");
        post2.setHeatScore(10);
        post2.setEssence(true);

        when(postRepo.findTrending(any(), any()))
                .thenReturn(new PageImpl<>(List.of(post1, post2)));
        when(userFollowedTagRepo.findByUserId("u1")).thenReturn(List.of());
        when(browsingHistoryRepo.findPostIdsByUserIdAndViewedAtAfter(eq("u1"), any())).thenReturn(List.of());
        when(postRepo.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(eq("u1"), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(postTagRepo.findByPostIdIn(anyList())).thenReturn(List.of());

        var author2 = createUser("u2", "author2", 3);
        var author3 = createUser("u3", "author3", 3);
        when(userRepo.findAllById(anyList())).thenReturn(List.of(author2, author3));

        var board = new Board(); board.setId("b1"); board.setName("TestBoard");
        when(boardRepo.findAllById(anyList())).thenReturn(List.of(board));
        when(boardService.buildTagsByPostId(anyList())).thenReturn(Collections.emptyMap());
        when(boardService.toPostCard(eq(post1), any(), any(), anyString()))
                .thenReturn(createPostCard(post1, author2, "TestBoard"));
        when(boardService.toPostCard(eq(post2), any(), any(), anyString()))
                .thenReturn(createPostCard(post2, author3, "TestBoard"));

        when(valueOps.get(contains("feed:recommend:u1:1"))).thenReturn(null);
        when(valueOps.get(contains("user_profile:u1"))).thenReturn(null);

        var result = feedService.feedRecommend("u1", 1, 20);
        assertEquals(2, result.items().size());
        // Essence post should be ranked first (higher quality score)
        assertEquals("Essence", result.items().get(0).title());
    }

    // ─── Weights loading ──────────────────────────────────

    @Test
    void loadWeights_shouldReturnDefaultsWhenNoConfig() {
        when(systemConfigRepo.findByConfigKey("recommendation_weights")).thenReturn(Optional.empty());
        var weights = feedService.loadWeights();
        assertEquals(0.4, weights.tagMatch());
        assertEquals(0.3, weights.heat());
        assertEquals(0.2, weights.freshness());
        assertEquals(0.1, weights.quality());
    }

    @Test
    void loadWeights_shouldReturnDefaultsOnError() {
        when(systemConfigRepo.findByConfigKey("recommendation_weights"))
                .thenThrow(new RuntimeException("DB error"));
        var weights = feedService.loadWeights();
        assertEquals(0.4, weights.tagMatch());
    }
}
