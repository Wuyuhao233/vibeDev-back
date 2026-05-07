package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.checkin.CheckinResponse;
import com.vibedev.dto.checkin.CheckinStatusResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class CheckinService {

    private static final String CHECKIN_KEY_PREFIX = "checkin:";
    private static final String STREAK_KEY_PREFIX = "checkin:streak:";

    private final StringRedisTemplate redis;
    private final PointsService pointsService;

    public CheckinService(StringRedisTemplate redis, PointsService pointsService) {
        this.redis = redis;
        this.pointsService = pointsService;
    }

    public CheckinResponse checkin(String userId) {
        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String checkinKey = CHECKIN_KEY_PREFIX + userId + ":" + today;

        if (Boolean.TRUE.equals(redis.hasKey(checkinKey))) {
            int streak = getStreak(userId);
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "今日已签到，连续签到 " + streak + " 天");
        }

        String streakKey = STREAK_KEY_PREFIX + userId;
        String lastCheckinDate = redis.opsForValue().get(streakKey + ":last_date");
        int streak;

        String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);

        if (lastCheckinDate == null || !lastCheckinDate.equals(yesterday)) {
            streak = 1;
        } else {
            String streakStr = redis.opsForValue().get(streakKey);
            streak = streakStr != null ? Integer.parseInt(streakStr) + 1 : 1;
        }

        redis.opsForValue().set(streakKey, String.valueOf(streak), Duration.ofHours(48));
        redis.opsForValue().set(streakKey + ":last_date", today, Duration.ofHours(48));
        redis.opsForValue().set(checkinKey, "1", Duration.ofDays(1));

        int basePoints = 2;
        int streakBonus = Math.max(0, streak - 6);
        int totalAwarded = basePoints + streakBonus;

        int newTotal = pointsService.addPoints(userId, totalAwarded, "checkin", null);

        return new CheckinResponse(totalAwarded, newTotal, streak, streakBonus);
    }

    public CheckinStatusResponse getCheckinStatus(String userId) {
        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String checkinKey = CHECKIN_KEY_PREFIX + userId + ":" + today;
        boolean checkedIn = Boolean.TRUE.equals(redis.hasKey(checkinKey));
        int streak = getStreak(userId);

        Integer todayPoints = null;
        if (checkedIn) {
            int streakBonus = Math.max(0, streak - 6);
            todayPoints = 2 + streakBonus;
        }

        return new CheckinStatusResponse(checkedIn, streak, todayPoints);
    }

    private int getStreak(String userId) {
        String streakKey = STREAK_KEY_PREFIX + userId;
        String streakStr = redis.opsForValue().get(streakKey);
        return streakStr != null ? Integer.parseInt(streakStr) : 0;
    }
}
