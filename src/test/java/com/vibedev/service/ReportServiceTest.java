package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.dto.appeal.CreateAppealRequest;
import com.vibedev.dto.appeal.HandleAppealRequest;
import com.vibedev.dto.report.CreateReportRequest;
import com.vibedev.dto.report.HandleReportRequest;
import com.vibedev.entity.Appeal;
import com.vibedev.entity.ModerationQueue;
import com.vibedev.entity.Post;
import com.vibedev.entity.Report;
import com.vibedev.entity.Reply;
import com.vibedev.entity.User;
import com.vibedev.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ReportServiceTest {

    @Mock ReportRepository reportRepo;
    @Mock PostRepository postRepo;
    @Mock ReplyRepository replyRepo;
    @Mock UserRepository userRepo;
    @Mock MuteService muteService;
    @Mock NotificationService notificationService;
    @Mock AppealRepository appealRepo;
    @Mock ModerationQueueRepository queueRepo;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepo, postRepo, replyRepo, userRepo,
                muteService, notificationService, appealRepo, queueRepo, redis);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void createReportShouldSucceed() {
        when(reportRepo.findByReporterIdAndTargetTypeAndTargetIdAndStatus(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(redis.opsForValue().get(anyString())).thenReturn(null);
        var post = new Post();
        post.setId("p1");
        post.setAuthorId("u2");
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        var dto = new CreateReportRequest("post", "p1", "spam", null);
        assertDoesNotThrow(() -> reportService.create("u1", dto));

        verify(reportRepo).save(any(Report.class));
    }

    @Test
    void duplicateReportShouldThrow() {
        when(reportRepo.findByReporterIdAndTargetTypeAndTargetIdAndStatus("u1", "post", "p1", "pending"))
                .thenReturn(Optional.of(new Report()));

        var dto = new CreateReportRequest("post", "p1", "spam", null);
        assertThrows(BusinessException.class, () -> reportService.create("u1", dto));
    }

    @Test
    void createReportWithOtherReasonNeedsDescription() {
        when(reportRepo.findByReporterIdAndTargetTypeAndTargetIdAndStatus(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(redis.opsForValue().get(anyString())).thenReturn(null);

        var dto = new CreateReportRequest("post", "p1", "other", null);
        assertThrows(BusinessException.class, () -> reportService.create("u1", dto));
    }

    @Test
    void checkBanShouldReturnNotBanned() {
        when(reportRepo.countByReporterIdAndIsMaliciousTrue("u1")).thenReturn(0L);
        when(redis.opsForValue().get(anyString())).thenReturn(null);

        var resp = reportService.checkBan("u1");
        assertFalse(resp.isBannedFromReporting());
        assertEquals(0, resp.maliciousCount());
    }

    @Test
    void handleReportIgnoredShouldSucceed() {
        var report = new Report();
        report.setId("rp1");
        report.setStatus("pending");
        report.setTargetType("post");
        report.setTargetId("p1");
        report.setReporterId("u1");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));

        var dto = new HandleReportRequest("ignored", "测试忽略", null, null);
        reportService.handle("rp1", dto, "admin1", "admin");

        assertEquals("processed", report.getStatus());
        assertEquals("ignored", report.getResult());
        verify(notificationService).create(eq("u1"), eq("report_handled"), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleReportBannedShouldSucceed() {
        var report = new Report();
        report.setId("rp1");
        report.setStatus("pending");
        report.setTargetType("post");
        report.setTargetId("p1");
        report.setReporterId("u1");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));

        var post = new Post();
        post.setId("p1");
        post.setAuthorId("u2");
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        var dto = new HandleReportRequest("banned", "违规禁言", "3d", "发布违规内容");
        reportService.handle("rp1", dto, "admin1", "admin");

        assertEquals("processed", report.getStatus());
        assertEquals("banned", report.getResult());
        verify(muteService).muteUser(eq("admin1"), eq("admin"), eq("u2"), eq(null), eq("3d"), eq("发布违规内容"));
    }

    @Test
    void handleAlreadyProcessedShouldThrow() {
        var report = new Report();
        report.setId("rp1");
        report.setStatus("processed");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));

        var dto = new HandleReportRequest("ignored", null, null, null);
        assertThrows(BusinessException.class, () -> reportService.handle("rp1", dto, "admin1", "admin"));
    }

    @Test
    void getByIdShouldReturnReport() {
        var report = new Report();
        report.setId("rp1");
        report.setTargetType("post");
        report.setTargetId("p1");
        report.setReporterId("u1");
        report.setReasonType("spam");
        report.setStatus("pending");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));

        var item = reportService.getById("rp1");
        assertEquals("rp1", item.id());
        assertEquals("pending", item.status());
    }

    @Test
    void listShouldReturnPaginated() {
        var report = new Report();
        report.setId("rp1");
        report.setReporterId("u1");
        report.setTargetType("post");
        report.setTargetId("p1");
        report.setReasonType("spam");
        report.setStatus("pending");
        when(reportRepo.findByStatusOrderByCreatedAtDesc(eq("pending"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report)));

        var result = reportService.list("pending", null, 1, 20, "admin1", "admin", null);
        assertEquals(1, result.items().size());
        assertEquals("rp1", result.items().get(0).id());
    }

    // ─── Appeal tests ────────────────────────────────────

    @Test
    void submitAppealShouldSucceed() {
        var report = new Report();
        report.setId("rp1");
        report.setStatus("processed");
        report.setResult("deleted");
        report.setTargetType("post");
        report.setTargetId("p1");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));

        var post = new Post();
        post.setId("p1");
        post.setAuthorId("u2");
        post.setBoardId("b1");
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(appealRepo.findByReportIdAndStatus("rp1", "pending")).thenReturn(Optional.empty());

        var dto = new CreateAppealRequest("我认为这是误判，请求复审");
        assertDoesNotThrow(() -> reportService.submitAppeal("rp1", "u2", dto));

        verify(appealRepo).save(any(Appeal.class));
        verify(queueRepo).save(any(ModerationQueue.class));
    }

    @Test
    void submitAppealShouldFailForNonAuthor() {
        var report = new Report();
        report.setId("rp1");
        report.setStatus("processed");
        report.setResult("deleted");
        report.setTargetType("post");
        report.setTargetId("p1");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));

        var post = new Post();
        post.setId("p1");
        post.setAuthorId("u2"); // author is u2
        post.setBoardId("b1");
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        var dto = new CreateAppealRequest("请求复审");
        // u3 tries to appeal — not the author
        assertThrows(BusinessException.class, () -> reportService.submitAppeal("rp1", "u3", dto));
    }

    @Test
    void submitAppealShouldFailForUnprocessedReport() {
        var report = new Report();
        report.setId("rp1");
        report.setStatus("pending");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));

        var dto = new CreateAppealRequest("请求复审");
        assertThrows(BusinessException.class, () -> reportService.submitAppeal("rp1", "u1", dto));
    }

    @Test
    void submitAppealShouldFailForIgnoredReport() {
        var report = new Report();
        report.setId("rp1");
        report.setStatus("processed");
        report.setResult("ignored");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));

        var dto = new CreateAppealRequest("请求复审");
        assertThrows(BusinessException.class, () -> reportService.submitAppeal("rp1", "u1", dto));
    }

    @Test
    void submitAppealShouldFailDuplicate() {
        var report = new Report();
        report.setId("rp1");
        report.setStatus("processed");
        report.setResult("deleted");
        report.setTargetType("post");
        report.setTargetId("p1");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));

        var post = new Post();
        post.setId("p1");
        post.setAuthorId("u2");
        post.setBoardId("b1");
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(appealRepo.findByReportIdAndStatus("rp1", "pending"))
                .thenReturn(Optional.of(new Appeal()));

        var dto = new CreateAppealRequest("请求复审");
        assertThrows(BusinessException.class, () -> reportService.submitAppeal("rp1", "u2", dto));
    }

    @Test
    void approveAppealShouldRestoreDeletedContent() {
        var appeal = new Appeal();
        appeal.setId("a1");
        appeal.setReportId("rp1");
        appeal.setAppellantId("u2");
        appeal.setStatus("pending");
        when(appealRepo.findById("a1")).thenReturn(Optional.of(appeal));

        var report = new Report();
        report.setId("rp1");
        report.setResult("deleted");
        report.setTargetType("post");
        report.setTargetId("p1");
        report.setReporterId("u1");
        report.setStatus("processed");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));

        var post = new Post();
        post.setId("p1");
        post.setDeleted(true);
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(queueRepo.findByTargetTypeAndTargetIdAndStatus("post", "p1", "pending"))
                .thenReturn(Optional.empty());

        reportService.approveAppeal("a1", "admin1");

        assertEquals("approved", appeal.getStatus());
        assertFalse(post.isDeleted());
        assertNull(post.getDeletedAt());
        verify(appealRepo).save(appeal);
    }

    @Test
    void approveAppealShouldUnmuteUser() {
        var appeal = new Appeal();
        appeal.setId("a1");
        appeal.setReportId("rp1");
        appeal.setAppellantId("u2");
        appeal.setStatus("pending");
        when(appealRepo.findById("a1")).thenReturn(Optional.of(appeal));

        var report = new Report();
        report.setId("rp1");
        report.setResult("banned");
        report.setTargetType("post");
        report.setTargetId("p1");
        report.setReporterId("u1");
        report.setStatus("processed");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));

        var post = new Post();
        post.setId("p1");
        post.setAuthorId("u2");
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(queueRepo.findByTargetTypeAndTargetIdAndStatus("post", "p1", "pending"))
                .thenReturn(Optional.empty());

        reportService.approveAppeal("a1", "admin1");

        assertEquals("approved", appeal.getStatus());
        verify(muteService).unmuteUser("admin1", "u2");
    }

    @Test
    void rejectAppealShouldSucceed() {
        var appeal = new Appeal();
        appeal.setId("a1");
        appeal.setReportId("rp1");
        appeal.setAppellantId("u2");
        appeal.setStatus("pending");
        when(appealRepo.findById("a1")).thenReturn(Optional.of(appeal));

        var report = new Report();
        report.setId("rp1");
        report.setTargetType("post");
        report.setTargetId("p1");
        when(reportRepo.findById("rp1")).thenReturn(Optional.of(report));
        when(queueRepo.findByTargetTypeAndTargetIdAndStatus("post", "p1", "pending"))
                .thenReturn(Optional.empty());

        reportService.rejectAppeal("a1", "admin1", "维持原判");

        assertEquals("rejected", appeal.getStatus());
        assertEquals("admin1", appeal.getHandlerId());
        assertEquals("维持原判", appeal.getHandlerNote());
        verify(appealRepo).save(appeal);
    }

    @Test
    void listAppealsShouldReturnPaginated() {
        var appeal = new Appeal();
        appeal.setId("a1");
        appeal.setReportId("rp1");
        appeal.setAppellantId("u1");
        appeal.setReason("请求复审");
        appeal.setStatus("pending");
        when(appealRepo.findByStatusOrderByCreatedAtAsc(eq("pending"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(appeal)));

        // Report
        var report = new Report();
        report.setId("rp1");
        report.setTargetType("post");
        report.setTargetId("p1");
        report.setReasonType("spam");
        when(reportRepo.findAllById(List.of("rp1"))).thenReturn(List.of(report));

        // Post content
        var post = new Post();
        post.setId("p1");
        post.setTitle("测试帖子标题");
        post.setContentMarkdown("这是测试内容");
        post.setBoardId("b1");
        when(postRepo.findAllById(List.of("p1"))).thenReturn(List.of(post));

        // User
        var appellant = new User();
        appellant.setId("u1");
        appellant.setUsername("testuser");
        when(userRepo.findAllById(any())).thenReturn(List.of(appellant));

        // No moderation queue entry with aiScore > 0
        when(queueRepo.findFirstByTargetTypeAndTargetIdAndAiScoreGreaterThanOrderByAiScoreDesc(
                eq("post"), eq("p1"), eq(0))).thenReturn(Optional.empty());

        var result = reportService.listAppeals("pending", 1, 20);
        assertEquals(1, result.items().size());
        assertEquals("a1", result.items().get(0).id());
        assertEquals("请求复审", result.items().get(0).appealReason());
        assertEquals("p1", result.items().get(0).contentId());
        assertEquals("测试帖子标题", result.items().get(0).contentTitle());
        assertEquals("这是测试内容", result.items().get(0).contentSummary());
        assertEquals("testuser", result.items().get(0).appellantUsername());
        assertEquals("spam", result.items().get(0).violationCategory());
    }

    @Test
    void listAppealsShouldReturnEmpty() {
        when(appealRepo.findByStatusOrderByCreatedAtAsc(eq("pending"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = reportService.listAppeals("pending", 1, 20);
        assertEquals(0, result.items().size());
    }

    @Test
    void listAppealsShouldIncludeAiScore() {
        var appeal = new Appeal();
        appeal.setId("a2");
        appeal.setReportId("rp2");
        appeal.setAppellantId("u2");
        appeal.setReason("AI误判");
        appeal.setStatus("pending");
        when(appealRepo.findByStatusOrderByCreatedAtAsc(eq("pending"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(appeal)));

        var report = new Report();
        report.setId("rp2");
        report.setTargetType("post");
        report.setTargetId("p2");
        report.setReasonType("spam");
        when(reportRepo.findAllById(List.of("rp2"))).thenReturn(List.of(report));

        var post = new Post();
        post.setId("p2");
        post.setTitle("AI检测帖子");
        post.setContentMarkdown("内容");
        post.setBoardId("b1");
        when(postRepo.findAllById(List.of("p2"))).thenReturn(List.of(post));

        var u = new User();
        u.setId("u2");
        u.setUsername("author2");
        when(userRepo.findAllById(any())).thenReturn(List.of(u));

        var mq = new ModerationQueue();
        mq.setAiScore(88);
        mq.setAiCategory("spam");
        when(queueRepo.findFirstByTargetTypeAndTargetIdAndAiScoreGreaterThanOrderByAiScoreDesc(
                eq("post"), eq("p2"), eq(0))).thenReturn(Optional.of(mq));

        var result = reportService.listAppeals("pending", 1, 20);
        assertEquals(1, result.items().size());
        assertEquals(88, result.items().get(0).aiScore());
        assertEquals("spam", result.items().get(0).violationCategory());
    }

    @Test
    void approveAppealShouldSetAuditStatus() {
        var appeal = new Appeal();
        appeal.setId("a3");
        appeal.setReportId("rp3");
        appeal.setAppellantId("u3");
        appeal.setStatus("pending");
        when(appealRepo.findById("a3")).thenReturn(Optional.of(appeal));

        var report = new Report();
        report.setId("rp3");
        report.setResult("deleted");
        report.setTargetType("post");
        report.setTargetId("p3");
        report.setReporterId("u1");
        report.setStatus("processed");
        when(reportRepo.findById("rp3")).thenReturn(Optional.of(report));

        var post = new Post();
        post.setId("p3");
        post.setDeleted(true);
        post.setAuditStatus("rejected");
        when(postRepo.findById("p3")).thenReturn(Optional.of(post));
        when(queueRepo.findByTargetTypeAndTargetIdAndStatus("post", "p3", "pending"))
                .thenReturn(Optional.empty());

        reportService.approveAppeal("a3", "admin1");

        assertEquals("approved", appeal.getStatus());
        assertFalse(post.isDeleted());
        assertEquals("approved", post.getAuditStatus());
    }

    @Test
    void rejectAppealShouldSetHandlerNote() {
        var appeal = new Appeal();
        appeal.setId("a4");
        appeal.setReportId("rp4");
        appeal.setAppellantId("u4");
        appeal.setStatus("pending");
        when(appealRepo.findById("a4")).thenReturn(Optional.of(appeal));

        var report = new Report();
        report.setId("rp4");
        report.setTargetType("post");
        report.setTargetId("p4");
        when(reportRepo.findById("rp4")).thenReturn(Optional.of(report));
        when(queueRepo.findByTargetTypeAndTargetIdAndStatus("post", "p4", "pending"))
                .thenReturn(Optional.empty());

        reportService.rejectAppeal("a4", "admin1", "驳回原因：内容违规");

        assertEquals("rejected", appeal.getStatus());
        assertEquals("admin1", appeal.getHandlerId());
        assertEquals("驳回原因：内容违规", appeal.getHandlerNote());
    }

    @Test
    void approveAppealShouldFailForNonPending() {
        var appeal = new Appeal();
        appeal.setId("a5");
        appeal.setStatus("approved");
        when(appealRepo.findById("a5")).thenReturn(Optional.of(appeal));

        assertThrows(BusinessException.class, () -> reportService.approveAppeal("a5", "admin1"));
    }

    @Test
    void rejectAppealShouldFailForNonPending() {
        var appeal = new Appeal();
        appeal.setId("a6");
        appeal.setStatus("rejected");
        when(appealRepo.findById("a6")).thenReturn(Optional.of(appeal));

        assertThrows(BusinessException.class, () -> reportService.rejectAppeal("a6", "admin1", "重复驳回"));
    }
}
