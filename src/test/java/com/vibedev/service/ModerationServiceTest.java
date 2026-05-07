package com.vibedev.service;

import com.vibedev.client.AiModerationClient;
import com.vibedev.client.AiModerationClient.AuditResult;
import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.moderation.ReviewQueueFilter;
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
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModerationServiceTest {

    @Mock ModerationQueueRepository queueRepo;
    @Mock ModerationLogRepository logRepo;
    @Mock AiAuditLogRepository aiAuditLogRepo;
    @Mock UserRepository userRepo;
    @Mock BoardRepository boardRepo;
    @Mock PostRepository postRepo;
    @Mock ReplyRepository replyRepo;
    @Mock ReportRepository reportRepo;
    @Mock NotificationService notificationService;
    @Mock AiModerationClient aiModerationClient;

    @InjectMocks ModerationService moderationService;

    private User createUser(String id, String role, String username) {
        var u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setNickname("Nick_" + username);
        u.setAvatarUrl("/avatar.png");
        u.setRole(role);
        u.setLevel(2);
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

    private ModerationQueue createQueueItem(String id, String targetType, String targetId,
                                             String status, String boardId, String authorId) {
        var mq = new ModerationQueue();
        mq.setId(id);
        mq.setTargetType(targetType);
        mq.setTargetId(targetId);
        mq.setContentSnapshot("test content");
        mq.setAiScore(65);
        mq.setAiCategory("spam");
        mq.setStatus(status);
        mq.setBoardId(boardId);
        mq.setAuthorId(authorId);
        mq.setTargetTitle("Test Title");
        mq.setPriority(0);
        return mq;
    }

    // ─── manualApprove ────────────────────────────────────

    @Test
    void manualApprove_shouldApprovePendingItem() {
        var item = createQueueItem("q1", "post", "p1", "pending", "b1", "u1");
        var post = new Post();
        post.setId("p1");
        post.setAuditStatus("pending_review");

        when(queueRepo.findById("q1")).thenReturn(Optional.of(item));
        when(boardRepo.findById("b1")).thenReturn(Optional.of(createBoard("b1", "Test")));
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        moderationService.manualApprove("q1", "mod1", "admin");

        assertEquals("approved", item.getStatus());
        assertEquals("mod1", item.getModeratorId());
        assertNotNull(item.getHandledAt());
        verify(queueRepo).save(item);
        verify(postRepo).save(any(Post.class));
        verify(logRepo).save(any(ModerationLog.class));
    }

    @Test
    void manualApprove_shouldThrowWhenAlreadyProcessed() {
        var item = createQueueItem("q1", "post", "p1", "approved", "b1", "u1");

        when(queueRepo.findById("q1")).thenReturn(Optional.of(item));

        var ex = assertThrows(BusinessException.class,
                () -> moderationService.manualApprove("q1", "mod1", "admin"));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void manualApprove_shouldThrowWhenNotFound() {
        when(queueRepo.findById("q1")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> moderationService.manualApprove("q1", "mod1", "admin"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void manualApprove_shouldAllowModerator() {
        var item = createQueueItem("q1", "reply", "r1", "pending", "b1", "u1");
        var reply = new Reply();
        reply.setId("r1");
        reply.setAuditStatus("pending_review");

        when(queueRepo.findById("q1")).thenReturn(Optional.of(item));
        when(boardRepo.findById("b1")).thenReturn(Optional.of(createBoard("b1", "Test")));
        when(replyRepo.findById("r1")).thenReturn(Optional.of(reply));

        moderationService.manualApprove("q1", "mod1", "moderator");

        assertEquals("approved", item.getStatus());
        verify(replyRepo).save(any(Reply.class));
    }

    // ─── manualReject ─────────────────────────────────────

    @Test
    void manualReject_shouldRejectPendingItem() {
        var item = createQueueItem("q1", "post", "p1", "pending", "b1", "u1");
        var post = new Post();
        post.setId("p1");
        post.setAuditStatus("pending_review");

        when(queueRepo.findById("q1")).thenReturn(Optional.of(item));
        when(boardRepo.findById("b1")).thenReturn(Optional.of(createBoard("b1", "Test")));
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        moderationService.manualReject("q1", "mod1", "admin", "违规内容");

        assertEquals("rejected", item.getStatus());
        assertEquals("mod1", item.getModeratorId());
        assertEquals("违规内容", item.getReason());
        assertNotNull(item.getHandledAt());
        verify(queueRepo).save(item);
        verify(notificationService).create(eq("u1"), eq("content_rejected"),
                anyString(), contains("违规内容"), anyString(), anyString());
    }

    @Test
    void manualReject_shouldThrowWhenAlreadyProcessed() {
        var item = createQueueItem("q1", "post", "p1", "rejected", "b1", "u1");

        when(queueRepo.findById("q1")).thenReturn(Optional.of(item));

        var ex = assertThrows(BusinessException.class,
                () -> moderationService.manualReject("q1", "mod1", "admin", "reason"));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    // ─── getReviewQueue ───────────────────────────────────

    @Test
    void getReviewQueue_shouldReturnQueueItems() {
        var filter = new ReviewQueueFilter("pending", null, 1, 20);
        var q1 = createQueueItem("q1", "post", "p1", "pending", "b1", "u1");
        var author = createUser("u1", "user", "user1");
        var board = createBoard("b1", "Frontend");

        when(queueRepo.findByStatusOrderByPriorityDescCreatedAtAsc(eq("pending"), any()))
                .thenReturn(new PageImpl<>(List.of(q1)));
        when(userRepo.findAllById(List.of("u1"))).thenReturn(List.of(author));
        when(boardRepo.findAllById(List.of("b1"))).thenReturn(List.of(board));
        when(queueRepo.countByStatus("pending")).thenReturn(1L);
        when(queueRepo.countApprovedSince(any())).thenReturn(5L);
        when(queueRepo.countRejectedSince(any())).thenReturn(2L);

        var result = moderationService.getReviewQueue(filter, "mod1", "admin");

        assertEquals(1, result.items().size());
        assertEquals("user1", result.items().get(0).author().username());
        assertEquals("Frontend", result.items().get(0).boardName());
        assertEquals(1, result.stats().pendingCount());
    }

    @Test
    void getReviewQueue_shouldFilterByTargetType() {
        var filter = new ReviewQueueFilter("pending", "post", 1, 20);
        var q1 = createQueueItem("q1", "post", "p1", "pending", "b1", "u1");
        var author = createUser("u1", "user", "user1");
        var board = createBoard("b1", "Frontend");

        when(queueRepo.findByStatusAndTargetTypeOrderByPriorityDescCreatedAtAsc(
                eq("pending"), eq("post"), any()))
                .thenReturn(new PageImpl<>(List.of(q1)));
        when(userRepo.findAllById(List.of("u1"))).thenReturn(List.of(author));
        when(boardRepo.findAllById(List.of("b1"))).thenReturn(List.of(board));
        when(queueRepo.countByStatus("pending")).thenReturn(1L);
        when(queueRepo.countApprovedSince(any())).thenReturn(0L);
        when(queueRepo.countRejectedSince(any())).thenReturn(0L);

        var result = moderationService.getReviewQueue(filter, "mod1", "admin");

        assertEquals(1, result.items().size());
    }

    @Test
    void getReviewQueue_shouldExcludeProcessedItemsByDefault() {
        var filter = new ReviewQueueFilter("pending", null, 1, 20);
        var q1 = createQueueItem("q1", "post", "p1", "pending", "b1", "u1");
        var author = createUser("u1", "user", "user1");
        var board = createBoard("b1", "Frontend");

        // Only pending items are returned; approved/rejected are excluded
        when(queueRepo.findByStatusOrderByPriorityDescCreatedAtAsc(eq("pending"), any()))
                .thenReturn(new PageImpl<>(List.of(q1)));
        when(userRepo.findAllById(List.of("u1"))).thenReturn(List.of(author));
        when(boardRepo.findAllById(List.of("b1"))).thenReturn(List.of(board));
        when(queueRepo.countByStatus("pending")).thenReturn(1L);
        when(queueRepo.countApprovedSince(any())).thenReturn(0L);
        when(queueRepo.countRejectedSince(any())).thenReturn(0L);

        var result = moderationService.getReviewQueue(filter, "admin1", "admin");

        assertEquals(1, result.items().size());
        assertEquals("pending", result.items().get(0).status());
    }

    // ─── getReviewStats ───────────────────────────────────

    @Test
    void getReviewStats_shouldReturnStats() {
        when(queueRepo.countByStatus("pending")).thenReturn(3L);
        when(queueRepo.countApprovedSince(any())).thenReturn(10L);
        when(queueRepo.countRejectedSince(any())).thenReturn(2L);
        when(reportRepo.countByStatus("pending")).thenReturn(1L);
        when(reportRepo.countByStatusAndProcessedAtAfter(eq("processed"), any())).thenReturn(5L);
        when(queueRepo.countAutoApprovedSince(any())).thenReturn(8L);
        when(queueRepo.countAutoRejectedSince(any())).thenReturn(1L);
        when(logRepo.countByActionSince(eq("manual_approved"), any())).thenReturn(2L);
        when(logRepo.countByActionSince(eq("manual_rejected"), any())).thenReturn(1L);
        when(aiAuditLogRepo.sumCostSince(any())).thenReturn(BigDecimal.valueOf(15.6));
        when(aiAuditLogRepo.countByCreatedAtAfter(any())).thenReturn(320L);
        when(aiAuditLogRepo.avgResponseTimeMsSince(any())).thenReturn(450.0);

        var result = moderationService.getReviewStats("admin1", "admin");

        assertEquals(3, result.queue().pendingCount());
        assertEquals(10, result.queue().todayApproved());
        assertEquals(2, result.queue().todayRejected());
        assertEquals(1, result.reports().pendingCount());
        assertEquals(5, result.reports().todayResolved());
        assertEquals(320, result.cost().dailyApiCalls());
        assertEquals(BigDecimal.valueOf(15.6), result.cost().monthlyCost());
        assertFalse(result.cost().isBudgetExceeded());
    }

    // ─── reviewAsync ──────────────────────────────────────

    @Test
    void reviewAsync_shouldAutoPassLowRiskContent() throws Exception {
        var post = new Post();
        post.setId("p1");
        post.setAuditStatus("pending");

        when(aiAuditLogRepo.sumCostSince(any())).thenReturn(BigDecimal.ZERO);
        when(aiModerationClient.audit(anyString()))
                .thenReturn(new AuditResult(30, "normal", "内容正常", false));
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        moderationService.reviewAsync("post", "p1", "normal content", "u1",
                "Test Title", "b1");

        // Async — wait briefly
        Thread.sleep(500);

        verify(aiAuditLogRepo).save(any(AiAuditLog.class));
        verify(postRepo).save(any(Post.class));
    }

    @Test
    void reviewAsync_shouldQueueMediumRiskContent() throws Exception {
        var post = new Post();
        post.setId("p1");
        post.setAuditStatus("pending");

        when(aiAuditLogRepo.sumCostSince(any())).thenReturn(BigDecimal.ZERO);
        when(aiModerationClient.audit(anyString()))
                .thenReturn(new AuditResult(65, "spam", "疑似违规", false));
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        moderationService.reviewAsync("post", "p1", "spam content", "u1",
                "Test Title", "b1");

        Thread.sleep(500);

        verify(queueRepo).save(any(ModerationQueue.class));
    }

    @Test
    void reviewAsync_shouldAutoRejectHighRiskContent() throws Exception {
        var post = new Post();
        post.setId("p1");
        post.setAuditStatus("pending");

        when(aiAuditLogRepo.sumCostSince(any())).thenReturn(BigDecimal.ZERO);
        when(aiModerationClient.audit(anyString()))
                .thenReturn(new AuditResult(90, "色情", "内容包含违规信息", false));
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        moderationService.reviewAsync("post", "p1", "bad content", "u1",
                "Test Title", "b1");

        Thread.sleep(500);

        verify(postRepo).save(any(Post.class));
        verify(notificationService).create(eq("u1"), eq("content_rejected"),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void reviewAsync_shouldDegradeWhenAiUnavailable() throws Exception {
        var post = new Post();
        post.setId("p1");
        post.setAuditStatus("pending");

        when(aiAuditLogRepo.sumCostSince(any())).thenReturn(BigDecimal.ZERO);
        when(aiModerationClient.audit(anyString()))
                .thenReturn(new AuditResult(-1, "unknown", "AI审核服务不可用", true));
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        moderationService.reviewAsync("post", "p1", "content", "u1",
                "Test Title", "b1");

        Thread.sleep(500);

        verify(queueRepo).save(any(ModerationQueue.class));
        verify(postRepo).save(any(Post.class));
    }

    // ─── Quality audit (V1.2) ──────────────────────────────

    @Test
    void sampleForQualityAudit_shouldReturnSample() {
        var p1 = new Post();
        p1.setId("p1");
        p1.setTitle("Post 1");
        p1.setAuthorId("u1");
        p1.setBoardId("b1");
        p1.setReplyCount(2);
        p1.setLikeCount(5);
        var p2 = new Post();
        p2.setId("p2");
        p2.setTitle("Post 2");
        p2.setAuthorId("u2");
        p2.setBoardId("b1");
        p2.setReplyCount(0);
        p2.setLikeCount(1);

        when(postRepo.findApprovedSince(any())).thenReturn(List.of(p1, p2));
        when(postRepo.countApprovedSince(any())).thenReturn(2L);

        var result = moderationService.sampleForQualityAudit(0.05);

        assertEquals(1, result.items().size()); // 5% of 2 = 0.1 -> ceil -> 1
        assertEquals(2, result.totalApprovedInMonth());
        assertEquals(0.05, result.sampleRate());
    }

    @Test
    void sampleForQualityAudit_shouldReturnEmptyWhenNoPosts() {
        when(postRepo.findApprovedSince(any())).thenReturn(List.of());
        when(postRepo.countApprovedSince(any())).thenReturn(0L);

        var result = moderationService.sampleForQualityAudit(0.05);

        assertEquals(0, result.items().size());
        assertEquals(0, result.totalApprovedInMonth());
    }

    @Test
    void reviewAsync_shouldDegradeWhenBudgetExceeded() throws Exception {
        var post = new Post();
        post.setId("p1");
        post.setAuditStatus("pending");

        when(aiAuditLogRepo.sumCostSince(any())).thenReturn(BigDecimal.valueOf(200));
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        moderationService.reviewAsync("post", "p1", "content", "u1",
                "Test Title", "b1");

        Thread.sleep(500);

        // Should enqueue without calling AI
        verify(aiModerationClient, never()).audit(anyString());
        verify(queueRepo).save(any(ModerationQueue.class));
    }
}
