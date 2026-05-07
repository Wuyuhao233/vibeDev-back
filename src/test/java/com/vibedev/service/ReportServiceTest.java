package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.dto.report.CreateReportRequest;
import com.vibedev.dto.report.HandleReportRequest;
import com.vibedev.entity.Post;
import com.vibedev.entity.Report;
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
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepo, postRepo, replyRepo, userRepo,
                muteService, notificationService, redis);
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
}
