package com.vibedev.dto.admin.dashboard;

import java.util.List;

public record DashboardStatsResponse(
        long todayPosts, long todayUsers, long pendingAudits,
        long pendingReports, long onlineUsers, long totalPosts,
        long totalUsers, long totalReplies,
        List<PostTrendPoint> postsTrend
) {
    public record PostTrendPoint(String date, long count) {}
}
