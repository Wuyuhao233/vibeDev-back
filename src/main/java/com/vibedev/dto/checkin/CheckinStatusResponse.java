package com.vibedev.dto.checkin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckinStatusResponse(
        @JsonProperty("has_checked_in_today") boolean hasCheckedInToday,
        @JsonProperty("consecutive_days") int consecutiveDays,
        @JsonProperty("today_points") Integer todayPoints
) {}
