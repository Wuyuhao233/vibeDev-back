package com.vibedev.service;

import com.vibedev.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock private PostRepository postRepo;
    @Mock private UserRepository userRepo;
    @Mock private ReportRepository reportRepo;
    @Mock private ModerationQueueRepository moderationQueueRepo;
    @Mock private ReplyRepository replyRepo;

    @InjectMocks
    private DashboardService service;

    @Test
    void getStats_returnsDashboardData() {
        when(postRepo.countApprovedSince(any(Instant.class))).thenReturn(10L);
        when(userRepo.countByCreatedAtSince(any(Instant.class))).thenReturn(5L);
        when(moderationQueueRepo.countByStatus("pending")).thenReturn(7L);
        when(reportRepo.countByStatus("pending")).thenReturn(3L);
        when(postRepo.count()).thenReturn(1000L);
        when(userRepo.count()).thenReturn(500L);
        when(replyRepo.count()).thenReturn(3000L);

        var result = service.getStats();

        assertEquals(10L, result.todayPosts());
        assertEquals(5L, result.todayUsers());
        assertEquals(7L, result.pendingAudits());
        assertEquals(3L, result.pendingReports());
        assertEquals(1000L, result.totalPosts());
        assertEquals(500L, result.totalUsers());
        assertEquals(3000L, result.totalReplies());
        assertNotNull(result.postsTrend());
        assertEquals(7, result.postsTrend().size());
    }
}
