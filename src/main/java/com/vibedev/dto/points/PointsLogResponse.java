package com.vibedev.dto.points;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PointsLogResponse(
        List<PointsLogItem> items,
        long total,
        int page,
        @JsonProperty("page_size") int pageSize
) {}
