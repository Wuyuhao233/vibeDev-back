package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.entity.PointsLog;
import com.vibedev.entity.User;
import com.vibedev.repository.PointsLogRepository;
import com.vibedev.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PointsServiceTest {

    @Mock PointsLogRepository pointsLogRepo;
    @Mock UserRepository userRepo;
    @Mock NotificationService notificationService;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock ZSetOperations<String, String> zSetOps;

    private PointsService pointsService;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForZSet()).thenReturn(zSetOps);
        pointsService = new PointsService(pointsLogRepo, userRepo, notificationService, redis);
    }

    private User createUser(String id, int points, int level) {
        var u = new User();
        u.setId(id);
        u.setUsername("user_" + id);
        u.setNickname("Nick" + id);
        u.setEmail(id + "@test.com");
        u.setPasswordHash("hash");
        u.setPoints(points);
        u.setLevel(level);
        return u;
    }

    // ─── addPoints ──────────────────────────────────────

    @Test
    void addPoints_shouldSucceed() {
        var user = createUser("u1", 100, 2);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        int newPoints = pointsService.addPoints("u1", 5, "create_post", "post", "p1");

        assertEquals(105, newPoints);
        assertEquals(105, user.getPoints());
        verify(pointsLogRepo).save(any(PointsLog.class));
    }

    @Test
    void addPoints_shouldThrowWhenUserNotFound() {
        when(userRepo.findById("u999")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> pointsService.addPoints("u999", 5, "create_post", "post", "p1"));
        assertEquals(30001, ex.getCode());
    }

    @Test
    void addPoints_shouldRespectDailyCap() {
        var user = createUser("u1", 100, 1);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(valueOps.increment(contains("create_post"))).thenReturn(11L);

        int newPoints = pointsService.addPoints("u1", 5, "create_post", "post", "p1");

        assertEquals(100, newPoints);
        verify(pointsLogRepo, never()).save(any());
    }

    @Test
    void addPoints_shouldAllowNoCapReason() {
        var user = createUser("u1", 100, 1);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        int newPoints = pointsService.addPoints("u1", 20, "post_featured", "post", "p1");

        assertEquals(120, newPoints);
        verify(pointsLogRepo).save(any(PointsLog.class));
    }

    // ─── deductPoints ───────────────────────────────────

    @Test
    void deductPoints_shouldSucceed() {
        var user = createUser("u1", 100, 2);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        int newPoints = pointsService.deductPoints("u1", 20, "muted_penalty", null);

        assertEquals(80, newPoints);
        assertEquals(80, user.getPoints());
        verify(pointsLogRepo).save(any(PointsLog.class));
    }

    @Test
    void deductPoints_shouldNotGoBelowZero() {
        var user = createUser("u1", 5, 1);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        int newPoints = pointsService.deductPoints("u1", 20, "muted_penalty", null);

        assertEquals(0, newPoints);
        assertEquals(0, user.getPoints());
    }

    // ─── checkLevelUp ───────────────────────────────────

    @Test
    void checkLevelUp_shouldUpgradeWhenThresholdReached() {
        var user = createUser("u1", 95, 1);
        user.setPoints(105);

        pointsService.checkLevelUp(user);

        assertEquals(2, user.getLevel());
        verify(userRepo).save(user);
        verify(notificationService).create(eq("u1"), eq("level_change"), eq("等级提升"), anyString(), isNull(), isNull());
    }

    @Test
    void checkLevelUp_shouldDowngradeWhenPointsDrop() {
        var user = createUser("u1", 500, 3);
        user.setPoints(95);

        pointsService.checkLevelUp(user);

        assertEquals(1, user.getLevel());
        verify(notificationService).create(eq("u1"), eq("level_change"), eq("等级下降"), anyString(), isNull(), isNull());
    }

    @Test
    void checkLevelUp_shouldNotChangeWhenSameLevel() {
        var user = createUser("u1", 150, 2);
        user.setPoints(250);

        pointsService.checkLevelUp(user);

        assertEquals(2, user.getLevel());
        verify(userRepo, never()).save(user);
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any());
    }

    // ─── getLevelByPoints ───────────────────────────────

    @Test
    void getLevelByPoints_shouldReturnCorrectLevels() {
        assertEquals(1, PointsService.getLevelByPoints(0));
        assertEquals(1, PointsService.getLevelByPoints(99));
        assertEquals(2, PointsService.getLevelByPoints(100));
        assertEquals(2, PointsService.getLevelByPoints(299));
        assertEquals(3, PointsService.getLevelByPoints(300));
        assertEquals(3, PointsService.getLevelByPoints(999));
        assertEquals(4, PointsService.getLevelByPoints(1000));
        assertEquals(5, PointsService.getLevelByPoints(3000));
        assertEquals(6, PointsService.getLevelByPoints(10000));
        assertEquals(6, PointsService.getLevelByPoints(99999));
    }

    // ─── getMinPoints / getMaxPoints ────────────────────

    @Test
    void getMinPoints_shouldReturnThresholds() {
        assertEquals(0, PointsService.getMinPoints(1));
        assertEquals(100, PointsService.getMinPoints(2));
        assertEquals(300, PointsService.getMinPoints(3));
        assertEquals(1000, PointsService.getMinPoints(4));
        assertEquals(3000, PointsService.getMinPoints(5));
        assertEquals(10000, PointsService.getMinPoints(6));
    }

    // ─── getLevelTitle ──────────────────────────────────

    @Test
    void getLevelTitle_shouldReturnChineseNames() {
        assertEquals("入门", PointsService.getLevelTitle(1));
        assertEquals("新秀", PointsService.getLevelTitle(2));
        assertEquals("常客", PointsService.getLevelTitle(3));
        assertEquals("达人", PointsService.getLevelTitle(4));
        assertEquals("专家", PointsService.getLevelTitle(5));
        assertEquals("宗师", PointsService.getLevelTitle(6));
    }

    // ─── getDailyPostLimit / getDailyReplyLimit ──────────

    @Test
    void getDailyPostLimit_shouldReturnCorrectLimits() {
        assertEquals(5, PointsService.getDailyPostLimit(1));
        assertEquals(10, PointsService.getDailyPostLimit(2));
        assertEquals(Integer.MAX_VALUE, PointsService.getDailyPostLimit(3));
    }

    @Test
    void getDailyReplyLimit_shouldReturnCorrectLimits() {
        assertEquals(15, PointsService.getDailyReplyLimit(1));
        assertEquals(Integer.MAX_VALUE, PointsService.getDailyReplyLimit(2));
        assertEquals(Integer.MAX_VALUE, PointsService.getDailyReplyLimit(3));
    }

    // ─── PointsLog save verification ────────────────────

    @Test
    void addPoints_shouldSaveLogWithCorrectReason() {
        var user = createUser("u1", 100, 1);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        pointsService.addPoints("u1", 5, "create_post", "post", "p1");

        var captor = ArgumentCaptor.forClass(PointsLog.class);
        verify(pointsLogRepo).save(captor.capture());
        var log = captor.getValue();
        assertEquals("u1", log.getUserId());
        assertEquals(5, log.getAmount());
        assertEquals("create_post", log.getReason());
        assertEquals("post", log.getRelatedType());
        assertEquals("p1", log.getRelatedId());
    }

    // ─── recalculateAllPoints ─────────────────────────────

    @Test
    void recalculateAllPoints_shouldUpdateMismatchedUsers() {
        var u1 = createUser("u1", 100, 2);
        var u2 = createUser("u2", 200, 3);
        when(userRepo.findAll()).thenReturn(List.of(u1, u2));
        when(pointsLogRepo.sumAmountByUserId("u1")).thenReturn(150);
        when(pointsLogRepo.sumAmountByUserId("u2")).thenReturn(200);

        int updated = pointsService.recalculateAllPoints();

        assertEquals(1, updated);
        assertEquals(150, u1.getPoints());
        assertEquals(2, u1.getLevel());
        assertEquals(200, u2.getPoints());
        assertEquals(3, u2.getLevel());
        verify(userRepo, atLeastOnce()).save(any(User.class));
    }

    @Test
    void recalculateAllPoints_shouldUpdateLevelOnRecalc() {
        var u = createUser("u1", 50, 1);
        when(userRepo.findAll()).thenReturn(List.of(u));
        when(pointsLogRepo.sumAmountByUserId("u1")).thenReturn(500);

        int updated = pointsService.recalculateAllPoints();

        assertEquals(1, updated);
        assertEquals(500, u.getPoints());
        assertEquals(3, u.getLevel());
    }

    @Test
    void recalculateAllPoints_shouldNotUpdateMatchingUsers() {
        var u = createUser("u1", 100, 2);
        when(userRepo.findAll()).thenReturn(List.of(u));
        when(pointsLogRepo.sumAmountByUserId("u1")).thenReturn(100);

        int updated = pointsService.recalculateAllPoints();

        assertEquals(0, updated);
        assertEquals(100, u.getPoints());
        assertEquals(2, u.getLevel());
    }
}
