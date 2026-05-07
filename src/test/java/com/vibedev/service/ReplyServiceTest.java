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
    @Mock NotificationService notificationService;
    @Mock MuteService muteService;
    @Mock SensitiveWordService sensitiveWordService;
    @Mock ModerationService moderationService;

    @InjectMocks ReplyService replyService;

    @BeforeEach
    void setUp() {
        replyService = new ReplyService(replyRepo, postRepo, userRepo, redis,
                notificationService, muteService, sensitiveWordService, moderationService);
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
        when(muteService.isUserBanned("u2")).thenReturn(true);

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

        when(replyRepo.findByPostIdAndIsDeletedFalseAndParentReplyIdIsNullOrderByCreatedAtAsc(eq("p1"), any()))
                .thenReturn(new PageImpl<>(List.of(reply)));
        when(replyRepo.findByPostIdAndIsDeletedFalseAndParentReplyIdInOrderByCreatedAtAsc(eq("p1"), anyList()))
                .thenReturn(List.of());
        when(userRepo.findAllById(List.of("u2"))).thenReturn(List.of(author));

        var result = replyService.listByPost("p1", 1, 20, null, "user");

        assertEquals(1, result.items().size());
        assertEquals("Nice post!", result.items().get(0).contentMarkdown());
        assertEquals(0, result.items().get(0).childReplies().size());
        assertEquals(1, result.total());
    }

    @Test
    void listByPost_shouldReturnEmptyList() {
        when(replyRepo.findByPostIdAndIsDeletedFalseAndParentReplyIdIsNullOrderByCreatedAtAsc(eq("p1"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = replyService.listByPost("p1", 1, 20, null, "user");

        assertEquals(0, result.items().size());
        assertEquals(0, result.total());
    }

    @Test
    void listByPost_shouldReturnNestedTree() {
        var rootReply = new Reply();
        rootReply.setId("r1");
        rootReply.setContentMarkdown("Root reply");
        rootReply.setContentHtml("<p>Root reply</p>");
        rootReply.setAuthorId("u2");
        rootReply.setPostId("p1");
        rootReply.setDepth(0);
        rootReply.setCreatedAt(Instant.now());

        var childReply = new Reply();
        childReply.setId("r2");
        childReply.setContentMarkdown("Child reply");
        childReply.setContentHtml("<p>Child reply</p>");
        childReply.setAuthorId("u3");
        childReply.setPostId("p1");
        childReply.setParentReplyId("r1");
        childReply.setDepth(1);
        childReply.setCreatedAt(Instant.now());

        var grandchildReply = new Reply();
        grandchildReply.setId("r3");
        grandchildReply.setContentMarkdown("Grandchild reply");
        grandchildReply.setContentHtml("<p>Grandchild</p>");
        grandchildReply.setAuthorId("u4");
        grandchildReply.setPostId("p1");
        grandchildReply.setParentReplyId("r2");
        grandchildReply.setDepth(2);
        grandchildReply.setCreatedAt(Instant.now());

        var author2 = createUser("u2", "user", 2);
        var author3 = createUser("u3", "user", 3);
        var author4 = createUser("u4", "user", 1);

        when(replyRepo.findByPostIdAndIsDeletedFalseAndParentReplyIdIsNullOrderByCreatedAtAsc(eq("p1"), any()))
                .thenReturn(new PageImpl<>(List.of(rootReply)));
        when(replyRepo.findByPostIdAndIsDeletedFalseAndParentReplyIdInOrderByCreatedAtAsc(eq("p1"), eq(List.of("r1"))))
                .thenReturn(List.of(childReply));
        when(replyRepo.findByPostIdAndIsDeletedFalseAndParentReplyIdInOrderByCreatedAtAsc(eq("p1"), eq(List.of("r2"))))
                .thenReturn(List.of(grandchildReply));
        when(userRepo.findAllById(List.of("u2", "u3", "u4"))).thenReturn(List.of(author2, author3, author4));

        var result = replyService.listByPost("p1", 1, 20, null, "user");

        assertEquals(1, result.items().size());
        var root = result.items().get(0);
        assertEquals("Root reply", root.contentMarkdown());
        assertEquals(0, root.depth());
        assertEquals(1, root.childReplies().size());

        var child = root.childReplies().get(0);
        assertEquals("Child reply", child.contentMarkdown());
        assertEquals(1, child.depth());
        assertEquals(1, child.childReplies().size());

        var grandchild = child.childReplies().get(0);
        assertEquals("Grandchild reply", grandchild.contentMarkdown());
        assertEquals(2, grandchild.depth());
        assertEquals(0, grandchild.childReplies().size());
    }

    // ─── create (V1.1 nested) ──────────────────────────

    @Test
    void create_shouldSucceedWithParentReply() {
        var user = createUser("u3", "user", 2);
        var post = createPost("p1", "u1");

        var parentReply = new Reply();
        parentReply.setId("r1");
        parentReply.setContentMarkdown("Parent");
        parentReply.setAuthorId("u2");
        parentReply.setPostId("p1");
        parentReply.setDepth(0);

        var dto = new CreateReplyRequest("Nested reply", "r1", "idem-nested");

        when(userRepo.findById("u3")).thenReturn(Optional.of(user));
        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(redis.hasKey(contains("cooldown"))).thenReturn(false);
        when(replyRepo.findByIdAndIsDeletedFalse("r1")).thenReturn(Optional.of(parentReply));

        var result = replyService.create("p1", dto, "u3");

        assertNotNull(result);
        assertEquals("Nested reply", result.contentMarkdown());
        assertEquals(1, result.depth());
        verify(replyRepo).save(any(Reply.class));
    }

    @Test
    void create_shouldSucceedWithDepth2Parent_MaxDepth() {
        var user = createUser("u4", "user", 3);
        var post = createPost("p1", "u1");

        var depth2Parent = new Reply();
        depth2Parent.setId("r2");
        depth2Parent.setContentMarkdown("Depth 2");
        depth2Parent.setAuthorId("u2");
        depth2Parent.setPostId("p1");
        depth2Parent.setDepth(2);

        var dto = new CreateReplyRequest("Max depth reply", "r2", "idem-maxdepth");

        when(userRepo.findById("u4")).thenReturn(Optional.of(user));
        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(redis.hasKey(contains("cooldown"))).thenReturn(false);
        when(replyRepo.findByIdAndIsDeletedFalse("r2")).thenReturn(Optional.of(depth2Parent));

        var result = replyService.create("p1", dto, "u4");

        assertNotNull(result);
        assertEquals(2, result.depth()); // stays at depth 2 (max)
    }

    @Test
    void create_shouldFailWhenParentReplyNotInSamePost() {
        var user = createUser("u3", "user", 2);
        var post = createPost("p1", "u1");

        var parentReply = new Reply();
        parentReply.setId("r1");
        parentReply.setContentMarkdown("Parent");
        parentReply.setAuthorId("u2");
        parentReply.setPostId("p2"); // different post
        parentReply.setDepth(0);

        var dto = new CreateReplyRequest("Reply", "r1", "idem-wrongpost");

        when(userRepo.findById("u3")).thenReturn(Optional.of(user));
        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(replyRepo.findByIdAndIsDeletedFalse("r1")).thenReturn(Optional.of(parentReply));

        var ex = assertThrows(BusinessException.class,
                () -> replyService.create("p1", dto, "u3"));
        assertEquals(ErrorCode.VALIDATION_CONTENT_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void create_shouldFailWhenParentReplyNotFound() {
        var user = createUser("u3", "user", 2);
        var post = createPost("p1", "u1");

        var dto = new CreateReplyRequest("Reply", "r999", "idem-notfound");

        when(userRepo.findById("u3")).thenReturn(Optional.of(user));
        when(postRepo.findByIdAndIsDeletedFalse("p1")).thenReturn(Optional.of(post));
        when(replyRepo.findByIdAndIsDeletedFalse("r999")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> replyService.create("p1", dto, "u3"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
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
