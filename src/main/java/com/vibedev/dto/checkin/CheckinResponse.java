package com.vibedev.dto.checkin;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CheckinResponse(
        @JsonProperty("points_awarded") int pointsAwarded,
        @JsonProperty("total_points") int totalPoints,
        @JsonProperty("consecutive_days") int consecutiveDays,
        @JsonProperty("streak_bonus") int streakBonus
) {}
