package com.vibedev.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.post.*;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostServiceTest {

    @Mock PostRepository postRepo;
    @Mock PostTagRepository postTagRepo;
    @Mock TagRepository tagRepo;
    @Mock TagService tagService;
    @Mock UserRepository userRepo;
    @Mock BoardRepository boardRepo;
    @Mock FavoriteRepository favoriteRepo;
    @Mock CollectionFolderRepository collectionFolderRepo;
    @Mock LikeRepository likeRepo;
    @Mock NotificationService notificationService;
    @Mock MuteService muteService;
    @Mock SensitiveWordService sensitiveWordService;
    @Mock ModerationService moderationService;
    @Mock PointsService pointsService;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks PostService postService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepo, postTagRepo, tagRepo, tagService, userRepo,
                boardRepo, favoriteRepo, collectionFolderRepo, likeRepo,
                notificationService, muteService, sensitiveWordService,
                moderationService, pointsService, redis, objectMapper);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(muteService.isUserBanned(anyString())).thenReturn(false);
        when(sensitiveWordService.hasSensitiveWord(anyString())).thenReturn(false);
    }

    private User createUser(String id, String role, int level) {
        var u = new User();
        u.setId(id);
        u.setUsername("user_" + id);
        u.setNickname("Nick" + id);
        u.setAvatarUrl("/avatar.png");
        u.setRole(role);
        u.setLevel(level);
        u.setPoints(100);
        u.setBanned(false);
        return u;
    }

    private Board createBoard(String id, String name) {
        var b = new Board();
        b.setId(id);
        b.setName(name);
        b.setStatus("active");
        return b;
    }

    // ─── create ──────────────────────────────────────────

    @Test
    void create_shouldSucceed() {
        var user = createUser("u1", "user", 2);
        var board = createBoard("b1", "Frontend");
        var dto = new CreatePostRequest("b1", List.of("React", "Vue"), "Hello World Post",
                "This is content", null, "idem-key-1");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));
        when(postRepo.countByTitleAndBoardIdAndIsDeletedFalse("Hello World Post", "b1")).thenReturn(0);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForValue().increment(contains("rate"))).thenReturn(1L);
        when(redis.opsForValue().increment(contains("daily"))).thenReturn(1L);
        // Mock tagService.findOrCreateTag to return tags
        var tag1 = new Tag(); tag1.setId("tag-react"); tag1.setName("React"); tag1.setBoardId("b1");
        var tag2 = new Tag(); tag2.setId("tag-vue"); tag2.setName("Vue"); tag2.setBoardId("b1");
        when(tagService.findOrCreateTag("b1", "React")).thenReturn(tag1);
        when(tagService.findOrCreateTag("b1", "Vue")).thenReturn(tag2);

        var result = postService.create(dto, "u1");

        assertNotNull(result);
        assertEquals("Hello World Post", result.title());
        verify(postRepo).save(any(Post.class));
        verify(postTagRepo, times(2)).save(any(PostTag.class));
    }

    @Test
    void create_shouldFailWhenTitleTooShort() {
        var user = createUser("u1", "user", 2);
        var dto = new CreatePostRequest("b1", List.of("t1"), "Hi", "content", null, "idem-key");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class, () -> postService.create(dto, "u1"));
        assertEquals(ErrorCode.VALIDATION_TITLE.getCode(), ex.getCode());
    }

    @Test
    void create_shouldSucceedWithNoTags() {
        var user = createUser("u1", "user", 2);
        var board = createBoard("b1", "Frontend");
        var dto = new CreatePostRequest("b1", List.of(), "Hello World Post", "content", null, "idem-key");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));
        when(postRepo.countByTitleAndBoardIdAndIsDeletedFalse("Hello World Post", "b1")).thenReturn(0);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForValue().increment(contains("rate"))).thenReturn(1L);
        when(redis.opsForValue().increment(contains("daily"))).thenReturn(1L);

        var result = postService.create(dto, "u1");
        assertNotNull(result);
        verify(postTagRepo, never()).save(any(PostTag.class));
    }

    @Test
    void create_shouldFailWhenTooManyTags() {
        var user = createUser("u1", "user", 2);
        var dto = new CreatePostRequest("b1", List.of("t1", "t2", "t3", "t4"),
                "Hello World Post", "content", null, "idem-key");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class, () -> postService.create(dto, "u1"));
        assertEquals(ErrorCode.VALIDATION_TAGS.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailWhenContentEmpty() {
        var user = createUser("u1", "user", 2);
        var dto = new CreatePostRequest("b1", List.of("t1"), "Hello World", "  ", null, "idem-key");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class, () -> postService.create(dto, "u1"));
        assertEquals(ErrorCode.VALIDATION_CONTENT_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailWhenUserBanned() {
        var user = createUser("u1", "user", 2);
        user.setBanned(true);
        var dto = new CreatePostRequest("b1", List.of("t1"), "Hello World Post", "content", null, "idem-key");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(muteService.isUserBanned("u1")).thenReturn(true);

        var ex = assertThrows(BusinessException.class, () -> postService.create(dto, "u1"));
        assertEquals(ErrorCode.BANNED.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailOnDuplicateTitle() {
        var user = createUser("u1", "user", 2);
        var board = createBoard("b1", "Frontend");
        var dto = new CreatePostRequest("b1", List.of("t1"), "Hello World Post", "content", null, "idem-key");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));
        when(postRepo.countByTitleAndBoardIdAndIsDeletedFalse("Hello World Post", "b1")).thenReturn(1);
        when(redis.opsForValue().increment(contains("rate"))).thenReturn(1L);
        when(redis.opsForValue().increment(contains("daily"))).thenReturn(1L);

        var ex = assertThrows(BusinessException.class, () -> postService.create(dto, "u1"));
        assertEquals(ErrorCode.VALIDATION_TITLE_DUPLICATE.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailOnRateLimit() {
        var user = createUser("u1", "user", 2);
        var board = createBoard("b1", "Frontend");
        var dto = new CreatePostRequest("b1", List.of("t1"), "Hello World Post", "content", null, "idem-key");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));
        when(redis.opsForValue().increment(contains("rate"))).thenReturn(4L);

        var ex = assertThrows(BusinessException.class, () -> postService.create(dto, "u1"));
        assertEquals(ErrorCode.RATE_LIMITED.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailOnLv1DailyLimit() {
        var user = createUser("u1", "user", 1);
        var board = createBoard("b1", "Frontend");
        var dto = new CreatePostRequest("b1", List.of("t1"), "Hello World Post", "content", null, "idem-key");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(boardRepo.findByIdAndIsDeletedFalse("b1")).thenReturn(Optional.of(board));
        when(redis.opsForValue().increment(contains("rate"))).thenReturn(1L);
        when(redis.opsForValue().increment(contains("daily"))).thenReturn(6L);

        var ex = assertThrows(BusinessException.class, () -> postService.create(dto, "u1"));
        assertEquals(ErrorCode.RATE_LIMITED.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailOnDuplicateIdempotencyKey() {
        when(redis.opsForValue().get(contains("idempotency:post:u1:idem-key")))
                .thenReturn("existing-post-id");

        var dto = new CreatePostRequest("b1", List.of("t1"), "Hello World Post", "content", null, "idem-key");
        var ex = assertThrows(BusinessException.class, () -> postService.create(dto, "u1"));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailWhenBoardNotFound() {
        var user = createUser("u1", "user", 2);
        var dto = new CreatePostRequest("b999", List.of("t1"), "Hello World Post", "content", null, "idem-key");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(boardRepo.findByIdAndIsDeletedFalse("b999")).thenReturn(Optional.empty());
        when(redis.opsForValue().increment(contains("rate"))).thenReturn(1L);
        when(redis.opsForValue().increment(contains("daily"))).thenReturn(1L);

        var ex = assertThrows(BusinessException.class, () -> postService.create(dto, "u1"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ─── getById ──────────────────────────────────────────

    @Test
    void getById_shouldReturnPost() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Test Post");
        post.setContentMarkdown("content");
        post.setContentHtml("<p>content</p>");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setAuditStatus("approved");
        post.setLikeCount(5);
        post.setReplyCount(3);
        post.setCreatedAt(Instant.now());

        var author = createUser("u1", "user", 3);
        var board = createBoard("b1", "Frontend");

        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(author));
        when(boardRepo.findById("b1")).thenReturn(Optional.of(board));
        when(postTagRepo.findByPostId("p1")).thenReturn(List.of());

        var result = postService.getById("p1", null);

        assertNotNull(result);
        assertEquals("Test Post", result.title());
        assertEquals("Frontend", result.boardName());
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(postRepo.findById("p999")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> postService.getById("p999", null));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getById_shouldThrowWhenPostDeletedAndNotAuthor() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Deleted Post");
        post.setContentMarkdown("content");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setDeleted(true);

        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(createUser("u1", "user", 1)));
        when(userRepo.findById("u2")).thenReturn(Optional.of(createUser("u2", "user", 1)));

        var ex = assertThrows(BusinessException.class,
                () -> postService.getById("p1", "u2"));
        assertEquals(ErrorCode.POST_DELETED.getCode(), ex.getCode());
    }

    @Test
    void getById_shouldAllowDeletedPostForAuthor() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("My Deleted Post");
        post.setContentMarkdown("content");
        post.setContentHtml("<p>content</p>");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setDeleted(true);
        post.setCreatedAt(Instant.now());

        var author = createUser("u1", "user", 3);
        var board = createBoard("b1", "Frontend");

        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(author));
        when(boardRepo.findById("b1")).thenReturn(Optional.of(board));
        when(postTagRepo.findByPostId("p1")).thenReturn(List.of());

        var result = postService.getById("p1", "u1");

        assertNotNull(result);
        assertTrue(result.isDeleted());
    }

    @Test
    void getById_shouldAllowDeletedPostForAdmin() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Deleted Post");
        post.setContentMarkdown("content");
        post.setContentHtml("<p>content</p>");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setDeleted(true);
        post.setCreatedAt(Instant.now());

        var author = createUser("u1", "user", 1);
        var admin = createUser("u2", "admin", 5);
        var board = createBoard("b1", "Frontend");

        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(author));
        when(userRepo.findById("u2")).thenReturn(Optional.of(admin));
        when(boardRepo.findById("b1")).thenReturn(Optional.of(board));
        when(postTagRepo.findByPostId("p1")).thenReturn(List.of());

        var result = postService.getById("p1", "u2");

        assertNotNull(result);
    }

    // ─── update ──────────────────────────────────────────

    @Test
    void update_shouldSucceedForAuthor() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Old Title");
        post.setContentMarkdown("old content");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setVersion(0);
        post.setAuditStatus("approved");

        var author = createUser("u1", "user", 3);
        var board = createBoard("b1", "Frontend");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(author));
        when(boardRepo.findById("b1")).thenReturn(Optional.of(board));
        when(postTagRepo.findByPostId("p1")).thenReturn(List.of());

        var dto = new UpdatePostRequest("New Title", "new content", null, null, 0);
        var result = postService.update("p1", dto, "u1", "user");

        assertNotNull(result);
        assertEquals("New Title", result.title());
        verify(postRepo).save(post);
    }

    @Test
    void update_shouldFailForNonAuthorRegularUser() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setContentMarkdown("content");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setVersion(0);

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));

        var dto = new UpdatePostRequest("New Title", "new content", null, null, 0);
        var ex = assertThrows(BusinessException.class,
                () -> postService.update("p1", dto, "u2", "user"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void update_shouldFailOnVersionConflict() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setContentMarkdown("content");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setVersion(5);

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));

        var dto = new UpdatePostRequest("New Title", null, null, null, 3);
        var ex = assertThrows(BusinessException.class,
                () -> postService.update("p1", dto, "u1", "user"));
        assertEquals(ErrorCode.VERSION_CONFLICT.getCode(), ex.getCode());
    }

    // ─── delete ──────────────────────────────────────────

    @Test
    void softDelete_shouldSucceedForAuthor() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setAuditStatus("approved");

        var author = createUser("u1", "user", 3);

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(author));

        assertDoesNotThrow(() -> postService.softDelete("p1", "u1", "user"));
        assertTrue(post.isDeleted());
        verify(postRepo).save(post);
    }

    @Test
    void softDelete_shouldFailForNonAuthorRegularUser() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));

        var ex = assertThrows(BusinessException.class,
                () -> postService.softDelete("p1", "u2", "user"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void softDelete_shouldSucceedForAdminOnOtherUsersPost() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");

        var author = createUser("u1", "user", 2);

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(author));

        postService.softDelete("p1", "u2", "admin");

        assertTrue(post.isDeleted());
        assertEquals("u2", post.getDeletedBy());
        verify(pointsService).deductPoints(eq("u1"), eq(10), eq("post_deleted_mod"), eq("post"), eq("p1"));
    }

    // ─── pin ──────────────────────────────────────────

    @Test
    void togglePin_shouldSetBoardPin() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setPinned(false);
        post.setPinType("none");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));

        var result = postService.togglePin("p1", "board", "u2", "admin");

        assertTrue(result.isPinned());
        assertEquals("board", result.pinType());
        verify(postRepo).save(post);
    }

    @Test
    void togglePin_shouldFailForRegularUser() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));

        var ex = assertThrows(BusinessException.class,
                () -> postService.togglePin("p1", "board", "u2", "user"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void togglePin_shouldFailGlobalPinForModerator() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));

        var ex = assertThrows(BusinessException.class,
                () -> postService.togglePin("p1", "global", "u2", "moderator"));
        assertEquals(ErrorCode.NOT_MODERATOR_OF_BOARD.getCode(), ex.getCode());
    }

    @Test
    void togglePin_shouldUnpinWhenAlreadyPinnedSameType() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setPinned(true);
        post.setPinType("board");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));

        var result = postService.togglePin("p1", "board", "u2", "moderator");

        assertFalse(result.isPinned());
        assertEquals("none", result.pinType());
    }

    // ─── essence ──────────────────────────────────────────

    @Test
    void toggleEssence_shouldSetEssence() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setEssence(false);

        var author = createUser("u1", "user", 3);

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(author));

        var result = postService.toggleEssence("p1", "u2", "admin");

        assertTrue(result.isEssence());
        verify(pointsService).addPoints(eq("u1"), eq(20), eq("post_featured"), eq("post"), eq("p1"));
        verify(postRepo).save(post);
    }

    @Test
    void toggleEssence_shouldFailForRegularUser() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));

        var ex = assertThrows(BusinessException.class,
                () -> postService.toggleEssence("p1", "u2", "user"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void toggleEssence_shouldNotDeductPointsOnUnEssence() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setEssence(true);

        var author = createUser("u1", "user", 3);
        author.setPoints(120);

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(author));

        var result = postService.toggleEssence("p1", "u2", "admin");

        assertFalse(result.isEssence());
        assertEquals(120, author.getPoints()); // no deduction
    }

    // ─── collect ──────────────────────────────────────────

    @Test
    void collect_shouldSucceed() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setCollectCount(0);

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(favoriteRepo.findByUserIdAndPostId("u2", "p1")).thenReturn(Optional.empty());

        var result = postService.collect("p1", "u2", null);

        assertTrue(result.collected());
        assertEquals(1, result.newCount());
        verify(favoriteRepo).save(any(Favorite.class));
        verify(postRepo).save(post);
    }

    @Test
    void collect_shouldFailWhenAlreadyCollected() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(favoriteRepo.findByUserIdAndPostId("u2", "p1"))
                .thenReturn(Optional.of(new Favorite()));

        var ex = assertThrows(BusinessException.class, () -> postService.collect("p1", "u2", null));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void collect_shouldFailWhenPostNotFound() {
        when(postRepo.findByIdAndIsDeletedFalse("p999")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> postService.collect("p999", "u1", null));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ─── uncollect ──────────────────────────────────────────

    @Test
    void uncollect_shouldSucceed() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setCollectCount(5);

        var fav = new Favorite();
        fav.setId("fav1");
        fav.setUserId("u2");
        fav.setPostId("p1");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(favoriteRepo.findByUserIdAndPostId("u2", "p1")).thenReturn(Optional.of(fav));

        var result = postService.uncollect("p1", "u2");

        assertFalse(result.collected());
        assertEquals(4, result.newCount());
        verify(favoriteRepo).delete(fav);
    }

    @Test
    void uncollect_shouldFailWhenNotCollected() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(favoriteRepo.findByUserIdAndPostId("u2", "p1")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> postService.uncollect("p1", "u2"));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    // ─── shareCard ──────────────────────────────────────────

    @Test
    void getShareCard_shouldReturnCard() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Share Test");
        post.setContentMarkdown("This is the post content for sharing.");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setCoverImageUrl("/cover.png");

        var author = createUser("u1", "user", 3);

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(author));

        var result = postService.getShareCard("p1");

        assertEquals("Share Test", result.title());
        assertEquals("user_u1", result.authorName());
        assertEquals("/cover.png", result.coverUrl());
    }

    @Test
    void getShareCard_shouldThrowWhenPostNotFound() {
        when(postRepo.findByIdAndIsDeletedFalse("p999")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> postService.getShareCard("p999"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ─── V1.2: collect with folder ──────────────────────────

    @Test
    void collect_withFolderId_shouldSucceed() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");
        post.setCollectCount(0);

        var folder = new CollectionFolder();
        folder.setId("f1");
        folder.setUserId("u2");
        folder.setName("My Folder");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(collectionFolderRepo.findByIdAndUserId("f1", "u2")).thenReturn(Optional.of(folder));
        when(favoriteRepo.findByUserIdAndPostId("u2", "p1")).thenReturn(Optional.empty());

        var result = postService.collect("p1", "u2", "f1");

        assertTrue(result.collected());
        assertEquals(1, result.newCount());
        verify(favoriteRepo).save(any(Favorite.class));
        verify(postRepo).save(post);
    }

    @Test
    void collect_shouldFailWhenFolderNotFound() {
        var post = new Post();
        post.setId("p1");
        post.setTitle("Title");
        post.setAuthorId("u1");
        post.setBoardId("b1");

        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(collectionFolderRepo.findByIdAndUserId("f999", "u2")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> postService.collect("p1", "u2", "f999"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ─── V1.2: share card generation ────────────────────────

    @Test
    void generateShareCard_shouldThrowWhenPostNotFound() {
        when(postRepo.findByIdAndIsDeletedFalse("p999")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> postService.generateShareCard("p999"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}
