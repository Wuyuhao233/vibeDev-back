package com.vibedev.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.board.*;
import com.vibedev.entity.*;
import com.vibedev.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardServiceTest {

    @Mock BoardRepository boardRepo;
    @Mock TagRepository tagRepo;
    @Mock PostRepository postRepo;
    @Mock PostTagRepository postTagRepo;
    @Mock UserRepository userRepo;
    @Mock LikeRepository likeRepo;
    @Mock FavoriteRepository favoriteRepo;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks BoardService boardService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        boardService = new BoardService(boardRepo, tagRepo, postRepo, postTagRepo,
                userRepo, likeRepo, favoriteRepo, redis, objectMapper);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    private Board createBoard(String id, String name, int sortOrder) {
        var b = new Board();
        b.setId(id);
        b.setName(name);
        b.setDescription("desc");
        b.setSortOrder(sortOrder);
        b.setStatus("active");
        b.setPostCount(10);
        return b;
    }

    private Tag createTag(String id, String boardId, String name, int sortOrder) {
        var t = new Tag();
        t.setId(id);
        t.setBoardId(boardId);
        t.setName(name);
        t.setSortOrder(sortOrder);
        t.setPostCount(5);
        return t;
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

    // ─── listAll ──────────────────────────────────────────

    @Test
    void listAll_shouldReturnBoardsWithTags() {
        var board = createBoard("b1", "Frontend", 1);
        when(boardRepo.findByStatusAndIsDeletedFalseOrderBySortOrder("active"))
                .thenReturn(List.of(board));
        when(tagRepo.findByBoardIdOrderBySortOrder("b1")).thenReturn(
                List.of(createTag("t1", "b1", "React", 1)));

        var result = boardService.listAll();

        assertEquals(1, result.size());
        assertEquals("Frontend", result.get(0).name());
        assertEquals(1, result.get(0).tags().size());
        assertEquals("React", result.get(0).tags().get(0).name());
    }

    @Test
    void listAll_shouldReturnEmptyListWhenNoBoards() {
        when(boardRepo.findByStatusAndIsDeletedFalseOrderBySortOrder("active"))
                .thenReturn(List.of());

        var result = boardService.listAll();

        assertTrue(result.isEmpty());
    }

    // ─── getBoardDetail ───────────────────────────────────

    @Test
    void getBoardDetail_shouldReturnBoard() {
        var board = createBoard("b1", "Backend", 2);
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));
        when(tagRepo.findByBoardIdOrderBySortOrder("b1")).thenReturn(List.of());

        var result = boardService.getBoardDetail("b1");

        assertEquals("Backend", result.name());
    }

    @Test
    void getBoardDetail_shouldThrowWhenNotFound() {
        when(boardRepo.findByIdAndIsDeletedFalse("b99")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> boardService.getBoardDetail("b99"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("版块不存在", ex.getMessage());
    }

    // ─── getBoardTags ─────────────────────────────────────

    @Test
    void getBoardTags_shouldReturnTags() {
        var board = createBoard("b1", "Frontend", 1);
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));
        when(tagRepo.findByBoardIdOrderBySortOrder("b1")).thenReturn(
                List.of(createTag("t1", "b1", "React", 1),
                        createTag("t2", "b1", "Vue", 2)));

        var result = boardService.getBoardTags("b1");

        assertEquals(2, result.size());
        assertEquals("React", result.get(0).name());
        assertEquals("Vue", result.get(1).name());
    }

    @Test
    void getBoardTags_shouldThrowWhenBoardNotFound() {
        when(boardRepo.findByIdAndIsDeletedFalse("b99")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> boardService.getBoardTags("b99"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ─── getBoardPosts ────────────────────────────────────

    @Test
    void getBoardPosts_shouldReturnPostsWithAuthors() {
        var board = createBoard("b1", "Frontend", 1);
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));

        var post = new Post();
        post.setId("p1");
        post.setTitle("Hello World");
        post.setContentMarkdown("This is a test post with some content");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setLikeCount(5);
        post.setReplyCount(3);
        post.setCollectCount(2);
        post.setCreatedAt(Instant.now());

        var postsPage = new PageImpl<>(List.of(post));
        when(postRepo.findByBoardIdHot(eq("b1"), isNull(), any())).thenReturn(postsPage);

        when(postTagRepo.findByPostIdIn(List.of("p1"))).thenReturn(List.of());

        var user = createUser("u1", "alice", 3);
        when(userRepo.findAllById(List.of("u1"))).thenReturn(List.of(user));

        var result = boardService.getBoardPosts("b1", null, "hot", 1, 20, null);

        assertEquals(1, result.items().size());
        var card = result.items().get(0);
        assertEquals("Hello World", card.title());
        assertEquals("alice", card.author().username());
        assertEquals(3, card.author().level());
        assertEquals("Frontend", card.boardName());
    }

    @Test
    void getBoardPosts_shouldHandleLatestSort() {
        var board = createBoard("b1", "Frontend", 1);
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));

        var post = new Post();
        post.setId("p1");
        post.setTitle("Latest Post");
        post.setContentMarkdown("content");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setCreatedAt(Instant.now());

        var postsPage = new PageImpl<>(List.of(post));
        when(postRepo.findByBoardIdLatest(eq("b1"), isNull(), any())).thenReturn(postsPage);

        when(postTagRepo.findByPostIdIn(List.of("p1"))).thenReturn(List.of());
        when(userRepo.findAllById(List.of("u1"))).thenReturn(List.of(createUser("u1", "alice", 1)));

        var result = boardService.getBoardPosts("b1", null, "latest", 1, 20, null);

        assertEquals(1, result.items().size());
        assertEquals("Latest Post", result.items().get(0).title());
        verify(postRepo).findByBoardIdLatest(eq("b1"), isNull(), any());
    }

    @Test
    void getBoardPosts_shouldHandleTrendingSort() {
        var board = createBoard("b1", "Frontend", 1);
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));

        var post = new Post();
        post.setId("p1");
        post.setTitle("Trending Post");
        post.setContentMarkdown("content");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setCreatedAt(Instant.now());

        var postsPage = new PageImpl<>(List.of(post));
        when(postRepo.findByBoardIdTrending(eq("b1"), isNull(), any(), any()))
                .thenReturn(postsPage);

        when(postTagRepo.findByPostIdIn(List.of("p1"))).thenReturn(List.of());
        when(userRepo.findAllById(List.of("u1"))).thenReturn(List.of(createUser("u1", "alice", 1)));

        var result = boardService.getBoardPosts("b1", null, "trending", 1, 20, null);

        assertEquals(1, result.items().size());
        assertEquals("Trending Post", result.items().get(0).title());
    }

    @Test
    void getBoardPosts_shouldThrowWhenBoardNotFound() {
        when(boardRepo.findByIdAndIsDeletedFalse("b99")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> boardService.getBoardPosts("b99", null, "hot", 1, 20, null));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getBoardPosts_shouldCapLimitAt50() {
        var board = createBoard("b1", "Frontend", 1);
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));
        when(postRepo.findByBoardIdHot(eq("b1"), isNull(), any())).thenReturn(
                new PageImpl<>(List.of()));

        // limit > 50 gets capped to 20
        var result = boardService.getBoardPosts("b1", null, "hot", 1, 100, null);

        assertEquals(50, result.pageSize());
    }

    @Test
    void getBoardPosts_shouldHandleEmptyBoard() {
        var board = createBoard("b1", "Frontend", 1);
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));
        when(postRepo.findByBoardIdHot(eq("b1"), isNull(), any())).thenReturn(
                new PageImpl<>(List.of()));

        var result = boardService.getBoardPosts("b1", null, "hot", 1, 20, null);

        assertEquals(0, result.items().size());
        assertEquals(0, result.total());
    }

    // ─── PostCard content summary ─────────────────────────

    @Test
    void toPostCard_shouldGenerateContentSummary() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Test");
        post.setContentMarkdown("## Heading\nThis is **bold** text with a [link](http://example.com).");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setCreatedAt(Instant.now());

        var user = createUser("u1", "alice", 2);
        var tags = List.<TagItem>of();

        var card = boardService.toPostCard(post, tags, user, "Frontend", null);

        assertTrue(card.contentSummary().contains("Heading"));
        assertTrue(card.contentSummary().contains("bold"));
        assertTrue(card.contentSummary().contains("link"));
        assertFalse(card.contentSummary().contains("##"));
        assertFalse(card.contentSummary().contains("**"));
        assertFalse(card.contentSummary().contains("]("));
    }

    @Test
    void toPostCard_shouldHandleNullAuthor() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Test");
        post.setContentMarkdown("content");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setCreatedAt(Instant.now());

        var card = boardService.toPostCard(post, List.of(), null, "Frontend", null);

        assertEquals("unknown", card.author().username());
        assertEquals("未知用户", card.author().nickname());
    }
}
