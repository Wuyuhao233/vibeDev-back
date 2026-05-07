package com.vibedev.service;

import com.vibedev.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckinServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock PointsService pointsService;

    private CheckinService checkinService;

    private final String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
    private final String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        checkinService = new CheckinService(redis, pointsService);
    }

    // ─── checkin: first time ────────────────────────────

    @Test
    void checkin_shouldSucceedFirstTime() {
        when(redis.hasKey(eq("checkin:u1:" + today))).thenReturn(false);
        when(valueOps.get(eq("checkin:streak:u1:last_date"))).thenReturn(null);
        when(pointsService.addPoints(eq("u1"), eq(2), eq("checkin"), isNull())).thenReturn(2);

        var result = checkinService.checkin("u1");

        assertEquals(2, result.pointsAwarded());
        assertEquals(2, result.totalPoints());
        assertEquals(1, result.consecutiveDays());
        assertEquals(0, result.streakBonus());
    }

    // ─── checkin: duplicate ─────────────────────────────

    @Test
    void checkin_shouldFailWhenAlreadyCheckedIn() {
        when(redis.hasKey(eq("checkin:u1:" + today))).thenReturn(true);
        when(valueOps.get(eq("checkin:streak:u1"))).thenReturn("5");

        var ex = assertThrows(BusinessException.class,
                () -> checkinService.checkin("u1"));
        assertEquals(40002, ex.getCode());
        assertTrue(ex.getMessage().contains("今日已签到"));
    }

    // ─── checkin: consecutive streak ────────────────────

    @Test
    void checkin_shouldContinueStreak() {
        when(redis.hasKey(eq("checkin:u1:" + today))).thenReturn(false);
        when(valueOps.get(eq("checkin:streak:u1:last_date"))).thenReturn(yesterday);
        when(valueOps.get(eq("checkin:streak:u1"))).thenReturn("3");
        when(pointsService.addPoints(eq("u1"), eq(2), eq("checkin"), isNull())).thenReturn(10);

        var result = checkinService.checkin("u1");

        assertEquals(4, result.consecutiveDays());
    }

    // ─── checkin: streak break ──────────────────────────

    @Test
    void checkin_shouldResetStreakWhenMissedDay() {
        when(redis.hasKey(eq("checkin:u1:" + today))).thenReturn(false);
        when(valueOps.get(eq("checkin:streak:u1:last_date"))).thenReturn("2020-01-01");
        when(pointsService.addPoints(eq("u1"), eq(2), eq("checkin"), isNull())).thenReturn(4);

        var result = checkinService.checkin("u1");

        assertEquals(1, result.consecutiveDays());
        assertEquals(0, result.streakBonus());
    }

    // ─── checkin: streak bonus ──────────────────────────

    @Test
    void checkin_shouldAwardStreakBonus() {
        when(redis.hasKey(eq("checkin:u1:" + today))).thenReturn(false);
        when(valueOps.get(eq("checkin:streak:u1:last_date"))).thenReturn(yesterday);
        when(valueOps.get(eq("checkin:streak:u1"))).thenReturn("7");
        // streak becomes 8, bonus = 8-6 = 2, total = 2+2 = 4
        when(pointsService.addPoints(eq("u1"), eq(4), eq("checkin"), isNull())).thenReturn(50);

        var result = checkinService.checkin("u1");

        assertEquals(8, result.consecutiveDays());
        assertEquals(2, result.streakBonus());
        assertEquals(4, result.pointsAwarded());
    }

    // ─── getCheckinStatus ───────────────────────────────

    @Test
    void getCheckinStatus_shouldReturnNotCheckedIn() {
        when(redis.hasKey(eq("checkin:u1:" + today))).thenReturn(false);
        when(valueOps.get(eq("checkin:streak:u1"))).thenReturn("3");

        var result = checkinService.getCheckinStatus("u1");

        assertFalse(result.hasCheckedInToday());
        assertEquals(3, result.consecutiveDays());
        assertNull(result.todayPoints());
    }

    @Test
    void getCheckinStatus_shouldReturnCheckedIn() {
        when(redis.hasKey(eq("checkin:u1:" + today))).thenReturn(true);
        when(valueOps.get(eq("checkin:streak:u1"))).thenReturn("7");

        var result = checkinService.getCheckinStatus("u1");

        assertTrue(result.hasCheckedInToday());
        assertEquals(7, result.consecutiveDays());
        assertEquals(3, result.todayPoints()); // 2 + max(0, 7-6) = 3
    }
}
