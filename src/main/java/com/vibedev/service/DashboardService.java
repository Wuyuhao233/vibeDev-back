package com.vibedev.service;

import com.vibedev.dto.admin.dashboard.DashboardStatsResponse;
import com.vibedev.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final ReportRepository reportRepo;
    private final ModerationQueueRepository moderationQueueRepo;
    private final ReplyRepository replyRepo;

    public DashboardService(PostRepository postRepo, UserRepository userRepo,
                            ReportRepository reportRepo,
                            ModerationQueueRepository moderationQueueRepo,
                            ReplyRepository replyRepo) {
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.reportRepo = reportRepo;
        this.moderationQueueRepo = moderationQueueRepo;
        this.replyRepo = replyRepo;
    }

    public DashboardStatsResponse getStats() {
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Today's posts
        long todayPosts = postRepo.countApprovedSince(todayStart);

        // Today's users
        long todayUsers = userRepo.countByCreatedAtSince(todayStart);

        // Pending audits
        long pendingAudits = moderationQueueRepo.countByStatus("pending");

        // Pending reports
        long pendingReports = reportRepo.countByStatus("pending");

        // Total counts
        long totalPosts = postRepo.count();
        long totalUsers = userRepo.count();
        long totalReplies = replyRepo.count();

        // 7-day trend
        List<DashboardStatsResponse.PostTrendPoint> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            Instant dayStart = LocalDate.now(ZoneOffset.UTC).minusDays(i).atStartOfDay(ZoneOffset.UTC).toInstant();
            long count = postRepo.countApprovedSince(dayStart);
            // Approximate: count posts created on that day
            String dateStr = LocalDate.now(ZoneOffset.UTC).minusDays(i).toString();
            trend.add(new DashboardStatsResponse.PostTrendPoint(dateStr, count));
        }

        return new DashboardStatsResponse(
                todayPosts, todayUsers, pendingAudits, pendingReports,
                0, // online users — placeholder (needs Redis active session tracking)
                totalPosts, totalUsers, totalReplies, trend
        );
    }
}
