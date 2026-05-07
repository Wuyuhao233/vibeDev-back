package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.points.LeaderboardEntry;
import com.vibedev.dto.points.LeaderboardResponse;
import com.vibedev.dto.points.PointsLogItem;
import com.vibedev.dto.points.PointsLogResponse;
import com.vibedev.entity.Post;
import com.vibedev.repository.PointsLogRepository;
import com.vibedev.repository.PostRepository;
import com.vibedev.repository.UserRepository;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.PointsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class PointsController {

    private final PointsLogRepository pointsLogRepo;
    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final StringRedisTemplate redis;
    private final PointsService pointsService;

    public PointsController(PointsLogRepository pointsLogRepo,
                            UserRepository userRepo,
                            PostRepository postRepo,
                            StringRedisTemplate redis,
                            PointsService pointsService) {
        this.pointsLogRepo = pointsLogRepo;
        this.userRepo = userRepo;
        this.postRepo = postRepo;
        this.redis = redis;
        this.pointsService = pointsService;
    }

    @GetMapping("/users/{username}/points-log")
    public ApiResponse<?> getPointsLog(@PathVariable String username,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int limit,
                                       Authentication auth) {
        String currentUserId = SecurityHelper.getUserId(auth);
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!user.getId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可查看自己的积分记录");
        }
        if (page < 1) page = 1;
        if (limit < 1 || limit > 50) limit = 20;

        var logPage = pointsLogRepo.findByUserIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(page - 1, limit));

        var items = logPage.getContent().stream()
                .map(log -> {
                    String relatedTitle = null;
                    if ("post".equals(log.getRelatedType()) && log.getRelatedId() != null) {
                        relatedTitle = postRepo.findById(log.getRelatedId())
                                .map(Post::getTitle)
                                .orElse(null);
                    }
                    return PointsLogItem.from(log, relatedTitle);
                })
                .toList();

        return ApiResponse.ok(new PointsLogResponse(
                items, logPage.getTotalElements(), page, limit));
    }

    @GetMapping("/users/leaderboard")
    public ApiResponse<?> getLeaderboard(
            @RequestParam(defaultValue = "total") String period,
            @RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > 100) limit = 20;

        String key = switch (period) {
            case "week" -> "leaderboard:week:" + java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ofPattern("YYYY-'W'ww"));
            case "month" -> "leaderboard:month:" + java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ofPattern("YYYY-MM"));
            default -> "leaderboard:total";
        };

        Set<String> topUsers = redis.opsForZSet().reverseRange(key, 0, limit - 1);
        if (topUsers == null || topUsers.isEmpty()) {
            return ApiResponse.ok(new LeaderboardResponse(List.of(), period));
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (String userId : topUsers) {
            var userOpt = userRepo.findById(userId);
            if (userOpt.isPresent()) {
                var u = userOpt.get();
                Double score = redis.opsForZSet().score(key, userId);
                entries.add(new LeaderboardEntry(
                        rank++,
                        u.getId(),
                        u.getUsername(),
                        u.getNickname(),
                        u.getAvatarUrl(),
                        u.getPoints(),
                        u.getLevel(),
                        PointsService.getLevelTitle(u.getLevel())
                ));
            }
        }

        return ApiResponse.ok(new LeaderboardResponse(entries, period));
    }
}
