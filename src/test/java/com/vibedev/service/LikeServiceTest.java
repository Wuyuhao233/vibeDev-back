package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.entity.Like;
import com.vibedev.entity.Post;
import com.vibedev.entity.Reply;
import com.vibedev.entity.User;
import com.vibedev.repository.LikeRepository;
import com.vibedev.repository.PostRepository;
import com.vibedev.repository.ReplyRepository;
import com.vibedev.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LikeServiceTest {

    @Mock LikeRepository likeRepo;
    @Mock PostRepository postRepo;
    @Mock ReplyRepository replyRepo;
    @Mock UserRepository userRepo;
    @Mock NotificationService notificationService;

    private LikeService likeService;

    @BeforeEach
    void setUp() {
        likeService = new LikeService(likeRepo, postRepo, replyRepo, userRepo, notificationService);
    }

    private Post createPost(String id, String authorId, int likeCount) {
        var p = new Post();
        p.setId(id);
        p.setAuthorId(authorId);
        p.setTitle("Test Post");
        p.setLikeCount(likeCount);
        return p;
    }

    private Reply createReply(String id, String authorId, int likeCount) {
        var r = new Reply();
        r.setId(id);
        r.setAuthorId(authorId);
        r.setLikeCount(likeCount);
        r.setContentMarkdown("test");
        return r;
    }

    private User createUser(String id, String username) {
        var u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setNickname(username);
        return u;
    }

    // ─── Like post ────────────────────────────────────────

    @Test
    void likePost_shouldSucceed() {
        var post = createPost("p1", "author1", 0);
        when(likeRepo.findByUserIdAndTargetTypeAndTargetId("u1", "post", "p1"))
                .thenReturn(Optional.empty());
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(createUser("u1", "liker")));

        var result = likeService.like("u1", "post", "p1");

        assertTrue(result.liked());
        assertEquals(1, result.newCount());
        assertEquals(1, post.getLikeCount());
        verify(likeRepo).save(any(Like.class));
        verify(notificationService).create(eq("author1"), eq("received_like"), any(), any(), eq("post"), eq("p1"));
    }

    @Test
    void likePost_shouldFailWhenAlreadyLiked() {
        var existing = new Like();
        existing.setId("like1");
        existing.setUserId("u1");
        existing.setTargetType("post");
        existing.setTargetId("p1");

        when(likeRepo.findByUserIdAndTargetTypeAndTargetId("u1", "post", "p1"))
                .thenReturn(Optional.of(existing));

        var ex = assertThrows(BusinessException.class,
                () -> likeService.like("u1", "post", "p1"));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void likeOwnPost_shouldNotSendNotification() {
        var post = createPost("p1", "u1", 0);
        when(likeRepo.findByUserIdAndTargetTypeAndTargetId("u1", "post", "p1"))
                .thenReturn(Optional.empty());
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u1")).thenReturn(Optional.of(createUser("u1", "self")));

        var result = likeService.like("u1", "post", "p1");

        assertTrue(result.liked());
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void likePost_shouldFailWhenPostNotFound() {
        when(likeRepo.findByUserIdAndTargetTypeAndTargetId("u1", "post", "p999"))
                .thenReturn(Optional.empty());
        when(postRepo.findById("p999")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> likeService.like("u1", "post", "p999"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ─── Unlike post ──────────────────────────────────────

    @Test
    void unlikePost_shouldSucceed() {
        var post = createPost("p1", "author1", 1);
        var existingLike = new Like();
        existingLike.setId("like1");
        existingLike.setUserId("u1");
        existingLike.setTargetType("post");
        existingLike.setTargetId("p1");

        when(likeRepo.findByUserIdAndTargetTypeAndTargetId("u1", "post", "p1"))
                .thenReturn(Optional.of(existingLike));
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        var result = likeService.unlike("u1", "post", "p1");

        assertFalse(result.liked());
        assertEquals(0, result.newCount());
        assertEquals(0, post.getLikeCount());
        verify(likeRepo).delete(existingLike);
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unlikePost_shouldBeIdempotent() {
        var post = createPost("p1", "author1", 5);
        when(likeRepo.findByUserIdAndTargetTypeAndTargetId("u1", "post", "p1"))
                .thenReturn(Optional.empty());
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        var result = likeService.unlike("u1", "post", "p1");

        assertFalse(result.liked());
        assertEquals(5, result.newCount());
        verify(likeRepo, never()).delete(any());
    }

    // ─── Like reply ───────────────────────────────────────

    @Test
    void likeReply_shouldSucceed() {
        var reply = createReply("r1", "author2", 0);
        when(likeRepo.findByUserIdAndTargetTypeAndTargetId("u1", "reply", "r1"))
                .thenReturn(Optional.empty());
        when(replyRepo.findById("r1")).thenReturn(Optional.of(reply));
        when(userRepo.findById("u1")).thenReturn(Optional.of(createUser("u1", "liker")));

        var result = likeService.like("u1", "reply", "r1");

        assertTrue(result.liked());
        assertEquals(1, result.newCount());
        assertEquals(1, reply.getLikeCount());
        verify(likeRepo).save(any(Like.class));
        verify(notificationService).create(eq("author2"), eq("received_like"), any(), any(), eq("reply"), eq("r1"));
    }

    @Test
    void unlikeReply_shouldSucceed() {
        var reply = createReply("r1", "author2", 5);
        var existingLike = new Like();
        existingLike.setId("like2");
        existingLike.setUserId("u1");
        existingLike.setTargetType("reply");
        existingLike.setTargetId("r1");

        when(likeRepo.findByUserIdAndTargetTypeAndTargetId("u1", "reply", "r1"))
                .thenReturn(Optional.of(existingLike));
        when(replyRepo.findById("r1")).thenReturn(Optional.of(reply));

        var result = likeService.unlike("u1", "reply", "r1");

        assertFalse(result.liked());
        assertEquals(4, result.newCount());
        assertEquals(4, reply.getLikeCount());
        verify(likeRepo).delete(existingLike);
    }

    @Test
    void likeReply_shouldFailWhenReplyNotFound() {
        when(likeRepo.findByUserIdAndTargetTypeAndTargetId("u1", "reply", "r999"))
                .thenReturn(Optional.empty());
        when(replyRepo.findById("r999")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> likeService.like("u1", "reply", "r999"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ─── Batch status ─────────────────────────────────────

    @Test
    void getStatus_shouldReturnCorrectStatuses() {
        when(likeRepo.existsByUserIdAndTargetTypeAndTargetId("u1", "post", "p1")).thenReturn(true);
        when(likeRepo.existsByUserIdAndTargetTypeAndTargetId("u1", "post", "p2")).thenReturn(false);
        when(likeRepo.countByTargetTypeAndTargetId("post", "p1")).thenReturn(10);
        when(likeRepo.countByTargetTypeAndTargetId("post", "p2")).thenReturn(3);

        var result = likeService.getStatus("u1", "post", List.of("p1", "p2"));

        assertEquals(2, result.items().size());

        var item1 = result.items().get(0);
        assertTrue(item1.isLiked());
        assertEquals(10, item1.count());
        assertEquals("p1", item1.targetId());

        var item2 = result.items().get(1);
        assertFalse(item2.isLiked());
        assertEquals(3, item2.count());
        assertEquals("p2", item2.targetId());
    }

    @Test
    void getStatus_shouldReturnEmptyForNullUser() {
        when(likeRepo.countByTargetTypeAndTargetId("post", "p1")).thenReturn(5);

        var result = likeService.getStatus(null, "post", List.of("p1"));

        assertEquals(1, result.items().size());
        assertFalse(result.items().get(0).isLiked());
        assertEquals(5, result.items().get(0).count());
    }

    @Test
    void getStatus_shouldHandleEmptyIds() {
        var result = likeService.getStatus("u1", "post", List.of());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void getStatus_shouldTruncateAt100() {
        var ids = new java.util.ArrayList<String>();
        for (int i = 0; i < 150; i++) {
            ids.add("p" + i);
        }
        when(likeRepo.existsByUserIdAndTargetTypeAndTargetId(any(), any(), any())).thenReturn(false);
        when(likeRepo.countByTargetTypeAndTargetId(any(), any())).thenReturn(0);

        var result = likeService.getStatus("u1", "post", ids);

        assertEquals(100, result.items().size());
    }

    // ─── Count boundary ───────────────────────────────────

    @Test
    void unlike_shouldNotGoBelowZero() {
        var post = createPost("p1", "author1", 0);
        var existingLike = new Like();
        existingLike.setId("like1");
        existingLike.setUserId("u1");
        existingLike.setTargetType("post");
        existingLike.setTargetId("p1");

        when(likeRepo.findByUserIdAndTargetTypeAndTargetId("u1", "post", "p1"))
                .thenReturn(Optional.of(existingLike));
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        var result = likeService.unlike("u1", "post", "p1");

        assertFalse(result.liked());
        assertEquals(0, result.newCount());
        assertEquals(0, post.getLikeCount());
    }
}
