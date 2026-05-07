package com.vibedev.dto.points;

import java.util.List;

public record LeaderboardResponse(
        List<LeaderboardEntry> entries,
        String period
) {}
