package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.reply.CreateReplyRequest;
import com.vibedev.dto.reply.UpdateReplyRequest;
import com.vibedev.entity.*;
import com.vibedev.repository.PostRepository;
import com.vibedev.repository.ReplyRepository;
import com.vibedev.repository.UserRepository;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReplyServiceTest {

    @Mock ReplyRepository replyRepo;
    @Mock PostRepository postRepo;
    @Mock UserRepository userRepo;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks ReplyService replyService;

    @BeforeEach
    void setUp() {
        replyService = new ReplyService(replyRepo, postRepo, userRepo, redis);
        when(redis.opsForValue()).thenReturn(valueOps);
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

    private Post createPost(String id, String authorId) {
        var p = new Post();
        p.setId(id);
        p.setTitle("Test Post");
        p.setAuthorId(authorId);
        p.setBoardId("b1");
        p.setContentMarkdown("content");
        p.setReplyCount(0);
        return p;
    }

    // ─── create ──────────────────────────────────────────

    @Test
    void create_shouldSucceed() {
        var user = createUser("u2", "user", 2);
        var post = createPost("p1", "u1");
        var dto = new CreateReplyRequest("Great post!", null, "idem-key-1");

        when(userRepo.findById("u2")).thenReturn(Optional.of(user));
        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(redis.hasKey(contains("cooldown"))).thenReturn(false);

        var result = replyService.create("p1", dto, "u2");

        assertNotNull(result);
        assertEquals("Great post!", result.contentMarkdown());
        assertEquals(0, result.depth());
        verify(replyRepo).save(any(Reply.class));
        verify(postRepo).save(post);
        assertEquals(1, post.getReplyCount());
    }

    @Test
    void create_shouldFailWhenContentEmpty() {
        var user = createUser("u2", "user", 2);
        var post = createPost("p1", "u1");
        var dto = new CreateReplyRequest("  ", null, "idem-key");

        when(userRepo.findById("u2")).thenReturn(Optional.of(user));
        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));

        var ex = assertThrows(BusinessException.class,
                () -> replyService.create("p1", dto, "u2"));
        assertEquals(ErrorCode.VALIDATION_CONTENT_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailWhenUserBanned() {
        var user = createUser("u2", "user", 2);
        user.setBanned(true);
        var post = createPost("p1", "u1");
        var dto = new CreateReplyRequest("Great post!", null, "idem-key");

        when(userRepo.findById("u2")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class,
                () -> replyService.create("p1", dto, "u2"));
        assertEquals(ErrorCode.BANNED.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailWhenPostNotFound() {
        var user = createUser("u2", "user", 2);
        var dto = new CreateReplyRequest("Great post!", null, "idem-key");

        when(userRepo.findById("u2")).thenReturn(Optional.of(user));
        when(postRepo.findByIdAndIsDeletedFalse("p999")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> replyService.create("p999", dto, "u2"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailOnCooldown() {
        var user = createUser("u2", "user", 2);
        var post = createPost("p1", "u1");
        var dto = new CreateReplyRequest("Great post!", null, "idem-key");

        when(userRepo.findById("u2")).thenReturn(Optional.of(user));
        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(redis.hasKey(contains("cooldown"))).thenReturn(true);
        when(redis.getExpire(contains("cooldown"), eq(TimeUnit.SECONDS))).thenReturn(5L);

        var ex = assertThrows(BusinessException.class,
                () -> replyService.create("p1", dto, "u2"));
        assertEquals(ErrorCode.RATE_LIMITED.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailOnDuplicateIdempotencyKey() {
        when(redis.opsForValue().get(contains("idempotency:reply:u2:idem-key")))
                .thenReturn("existing-reply-id");

        var dto = new CreateReplyRequest("Great post!", null, "idem-key");
        var ex = assertThrows(BusinessException.class,
                () -> replyService.create("p1", dto, "u2"));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailOnLv1DailyReplyLimit() {
        var user = createUser("u2", "user", 1);
        var post = createPost("p1", "u1");
        var dto = new CreateReplyRequest("Great post!", null, "idem-key");

        when(userRepo.findById("u2")).thenReturn(Optional.of(user));
        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(redis.hasKey(contains("cooldown"))).thenReturn(false);
        when(redis.opsForValue().increment(contains("daily"))).thenReturn(16L);

        var ex = assertThrows(BusinessException.class,
                () -> replyService.create("p1", dto, "u2"));
        assertEquals(ErrorCode.RATE_LIMITED.getCode(), ex.getCode());
    }

    // ─── listByPost ──────────────────────────────────────────

    @Test
    void listByPost_shouldReturnReplies() {
        var reply = new Reply();
        reply.setId("r1");
        reply.setContentMarkdown("Nice post!");
        reply.setContentHtml("<p>Nice post!</p>");
        reply.setAuthorId("u2");
        reply.setPostId("p1");
        reply.setDepth(0);
        reply.setCreatedAt(Instant.now());

        var author = createUser("u2", "user", 2);

        when(replyRepo.findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(eq("p1"), any()))
                .thenReturn(new PageImpl<>(List.of(reply)));
        when(userRepo.findAllById(List.of("u2"))).thenReturn(List.of(author));

        var result = replyService.listByPost("p1", 1, 20, null);

        assertEquals(1, result.items().size());
        assertEquals("Nice post!", result.items().get(0).contentMarkdown());
        assertEquals(1, result.total());
    }

    @Test
    void listByPost_shouldReturnEmptyList() {
        when(replyRepo.findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(eq("p1"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = replyService.listByPost("p1", 1, 20, null);

        assertEquals(0, result.items().size());
        assertEquals(0, result.total());
    }

    // ─── update ──────────────────────────────────────────

    @Test
    void update_shouldSucceedForAuthor() {
        var reply = new Reply();
        reply.setId("r1");
        reply.setContentMarkdown("old content");
        reply.setContentHtml("<p>old</p>");
        reply.setAuthorId("u2");
        reply.setPostId("p1");
        reply.setVersion(0);

        var author = createUser("u2", "user", 2);

        when(replyRepo.findByIdAndIsDeletedFalse("r1")).thenReturn(Optional.of(reply));
        when(userRepo.findById("u2")).thenReturn(Optional.of(author));

        var dto = new UpdateReplyRequest("updated content", 0);
        var result = replyService.update("r1", dto, "u2", "user");

        assertNotNull(result);
        assertEquals("updated content", result.contentMarkdown());
        verify(replyRepo).save(reply);
    }

    @Test
    void update_shouldFailForNonAuthor() {
        var reply = new Reply();
        reply.setId("r1");
        reply.setContentMarkdown("content");
        reply.setAuthorId("u1");
        reply.setPostId("p1");
        reply.setVersion(0);

        when(replyRepo.findByIdAndIsDeletedFalse("r1")).thenReturn(Optional.of(reply));

        var dto = new UpdateReplyRequest("hacked content", 0);
        var ex = assertThrows(BusinessException.class,
                () -> replyService.update("r1", dto, "u2", "user"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void update_shouldFailOnVersionConflict() {
        var reply = new Reply();
        reply.setId("r1");
        reply.setContentMarkdown("content");
        reply.setAuthorId("u2");
        reply.setPostId("p1");
        reply.setVersion(3);

        when(replyRepo.findByIdAndIsDeletedFalse("r1")).thenReturn(Optional.of(reply));

        var dto = new UpdateReplyRequest("updated content", 1);
        var ex = assertThrows(BusinessException.class,
                () -> replyService.update("r1", dto, "u2", "user"));
        assertEquals(ErrorCode.VERSION_CONFLICT.getCode(), ex.getCode());
    }

    @Test
    void update_shouldSucceedForAdminOnOthersReply() {
        var reply = new Reply();
        reply.setId("r1");
        reply.setContentMarkdown("content");
        reply.setContentHtml("<p>content</p>");
        reply.setAuthorId("u1");
        reply.setPostId("p1");
        reply.setVersion(0);

        var admin = createUser("u3", "admin", 5);

        when(replyRepo.findByIdAndIsDeletedFalse("r1")).thenReturn(Optional.of(reply));
        when(userRepo.findById("u1")).thenReturn(Optional.of(createUser("u1", "user", 1)));

        var dto = new UpdateReplyRequest("moderated content", 0);
        var result = replyService.update("r1", dto, "u3", "admin");

        assertNotNull(result);
        assertEquals("moderated content", result.contentMarkdown());
    }

    // ─── softDelete ──────────────────────────────────────────

    @Test
    void softDelete_shouldSucceedForAuthor() {
        var reply = new Reply();
        reply.setId("r1");
        reply.setContentMarkdown("content");
        reply.setAuthorId("u2");
        reply.setPostId("p1");

        when(replyRepo.findByIdAndIsDeletedFalse("r1")).thenReturn(Optional.of(reply));

        replyService.softDelete("r1", "u2", "user");

        assertTrue(reply.isDeleted());
        assertNotNull(reply.getDeletedAt());
        verify(replyRepo).save(reply);
    }

    @Test
    void softDelete_shouldFailForNonAuthor() {
        var reply = new Reply();
        reply.setId("r1");
        reply.setContentMarkdown("content");
        reply.setAuthorId("u1");
        reply.setPostId("p1");

        when(replyRepo.findByIdAndIsDeletedFalse("r1")).thenReturn(Optional.of(reply));

        var ex = assertThrows(BusinessException.class,
                () -> replyService.softDelete("r1", "u2", "user"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void softDelete_shouldSucceedForAdmin() {
        var reply = new Reply();
        reply.setId("r1");
        reply.setContentMarkdown("content");
        reply.setAuthorId("u1");
        reply.setPostId("p1");

        when(replyRepo.findByIdAndIsDeletedFalse("r1")).thenReturn(Optional.of(reply));

        replyService.softDelete("r1", "u3", "admin");

        assertTrue(reply.isDeleted());
        assertNotNull(reply.getDeletedAt());
    }

    @Test
    void softDelete_shouldThrowWhenReplyNotFound() {
        when(replyRepo.findByIdAndIsDeletedFalse("r999")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> replyService.softDelete("r999", "u1", "user"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}
