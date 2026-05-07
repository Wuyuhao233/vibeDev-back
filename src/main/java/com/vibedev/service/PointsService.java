package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.entity.PointsLog;
import com.vibedev.entity.User;
import com.vibedev.repository.PointsLogRepository;
import com.vibedev.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
public class PointsService {

    private static final Logger log = LoggerFactory.getLogger(PointsService.class);

    private static final Map<String, PointsRule> RULES = Map.of(
        "create_post", new PointsRule(5, 10),
        "create_reply", new PointsRule(2, 20),
        "receive_like", new PointsRule(1, 50),
        "receive_bookmark", new PointsRule(2, 0),
        "post_featured", new PointsRule(20, 0),
        "checkin", new PointsRule(2, 1)
    );

    private static final String DAILY_KEY_PREFIX = "points:daily:";

    private final PointsLogRepository pointsLogRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;
    private final StringRedisTemplate redis;

    public PointsService(PointsLogRepository pointsLogRepo,
                         UserRepository userRepo,
                         NotificationService notificationService,
                         StringRedisTemplate redis) {
        this.pointsLogRepo = pointsLogRepo;
        this.userRepo = userRepo;
        this.notificationService = notificationService;
        this.redis = redis;
    }

    @Transactional
    public int addPoints(String userId, int amount, String reason, String relatedId) {
        return addPoints(userId, amount, reason, null, relatedId);
    }

    @Transactional
    public int addPoints(String userId, int amount, String reason, String relatedType, String relatedId) {
        var rule = RULES.get(reason);
        if (rule != null && rule.dailyCap > 0) {
            String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
            String key = DAILY_KEY_PREFIX + userId + ":" + reason + ":" + today;
            Long count = redis.opsForValue().increment(key);
            if (count == 1) {
                redis.expire(key, Duration.ofDays(1));
            }
            if (count != null && count > rule.dailyCap) {
                var user = userRepo.findById(userId).orElse(null);
                return user != null ? user.getPoints() : 0;
            }
        }

        var user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        user.setPoints(user.getPoints() + amount);
        userRepo.save(user);

        saveLog(userId, amount, reason, relatedType, relatedId);
        checkLevelUp(user);
        updateLeaderboard(userId, user.getPoints());

        return user.getPoints();
    }

    @Transactional
    public int deductPoints(String userId, int amount, String reason, String relatedId) {
        return deductPoints(userId, amount, reason, null, relatedId);
    }

    @Transactional
    public int deductPoints(String userId, int amount, String reason, String relatedType, String relatedId) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        int newPoints = Math.max(user.getPoints() - amount, 0);
        user.setPoints(newPoints);
        userRepo.save(user);

        saveLog(userId, -amount, reason, relatedType, relatedId);
        checkLevelUp(user);
        updateLeaderboard(userId, user.getPoints());

        return user.getPoints();
    }

    public void checkLevelUp(User user) {
        int newLevel = getLevelByPoints(user.getPoints());
        int oldLevel = user.getLevel();
        if (newLevel != oldLevel) {
            user.setLevel(newLevel);
            userRepo.save(user);

            String title = newLevel > oldLevel ? "等级提升" : "等级下降";
            String[] titles = {"入门", "新秀", "常客", "达人", "专家", "宗师"};
            String levelTitle = titles[newLevel - 1];
            String body = (newLevel > oldLevel ? "恭喜！你已升级为 " : "你的等级已调整为 ")
                    + levelTitle + " Lv." + newLevel;
            notificationService.create(user.getId(), "level_change", title, body, null, null);
            log.info("User {} level changed: {} -> {}", user.getId(), oldLevel, newLevel);
        }
    }

    public static int getLevelByPoints(int points) {
        if (points >= 10000) return 6;
        if (points >= 3000) return 5;
        if (points >= 1000) return 4;
        if (points >= 300) return 3;
        if (points >= 100) return 2;
        return 1;
    }

    public static int getMinPoints(int level) {
        return switch (level) {
            case 6 -> 10000;
            case 5 -> 3000;
            case 4 -> 1000;
            case 3 -> 300;
            case 2 -> 100;
            default -> 0;
        };
    }

    public static int getMaxPoints(int level) {
        return switch (level) {
            case 6 -> Integer.MAX_VALUE;
            case 5 -> 9999;
            case 4 -> 2999;
            case 3 -> 999;
            case 2 -> 299;
            default -> 99;
        };
    }

    public static String getLevelTitle(int level) {
        return switch (level) {
            case 6 -> "宗师";
            case 5 -> "专家";
            case 4 -> "达人";
            case 3 -> "常客";
            case 2 -> "新秀";
            default -> "入门";
        };
    }

    public static int getDailyPostLimit(int level) {
        return switch (level) {
            case 1 -> 5;
            case 2 -> 10;
            default -> Integer.MAX_VALUE;
        };
    }

    public static int getDailyReplyLimit(int level) {
        return switch (level) {
            case 1 -> 15;
            default -> Integer.MAX_VALUE;
        };
    }

    private void updateLeaderboard(String userId, int points) {
        String weekKey = "leaderboard:week:" + LocalDate.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("YYYY-'W'ww"));
        String monthKey = "leaderboard:month:" + LocalDate.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("YYYY-MM"));
        redis.opsForZSet().add("leaderboard:total", userId, points);
        redis.opsForZSet().add(weekKey, userId, points);
        redis.opsForZSet().add(monthKey, userId, points);
    }

    private void saveLog(String userId, int amount, String reason, String relatedType, String relatedId) {
        var log = new PointsLog();
        log.setId(UUID.randomUUID().toString());
        log.setUserId(userId);
        log.setAmount(amount);
        log.setReason(reason);
        log.setRelatedType(relatedType);
        log.setRelatedId(relatedId);
        pointsLogRepo.save(log);
    }

    @Transactional
    public int recalculateAllPoints() {
        var allUsers = userRepo.findAll();
        int updatedCount = 0;
        for (var user : allUsers) {
            int calculatedPoints = pointsLogRepo.sumAmountByUserId(user.getId());
            if (calculatedPoints != user.getPoints()) {
                user.setPoints(calculatedPoints);
                int newLevel = getLevelByPoints(calculatedPoints);
                user.setLevel(newLevel);
                userRepo.save(user);
                updateLeaderboard(user.getId(), calculatedPoints);
                updatedCount++;
            }
        }
        log.info("Recalculated points for {} users ({} changed)", allUsers.size(), updatedCount);
        return updatedCount;
    }

    public record PointsRule(int points, int dailyCap) {}
}
