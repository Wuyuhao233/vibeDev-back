package com.vibedev.dto.points;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LeaderboardEntry(
        int rank,
        @JsonProperty("user_id") String userId,
        String username,
        String nickname,
        @JsonProperty("avatar_url") String avatarUrl,
        int points,
        int level,
        @JsonProperty("level_title") String levelTitle
) {}
