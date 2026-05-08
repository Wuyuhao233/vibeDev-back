package com.vibedev.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResponse(
        List<SearchResultItem> items,
        long total,
        int page,
        int pageSize,
        String searchTime,
        String suggestion) {
}
