package com.vibedev.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginatedResponse<T>(
        List<T> items,
        long total,
        int page,
        int pageSize) {

    public static <T> PaginatedResponse<T> of(List<T> items, long total, int page, int pageSize) {
        return new PaginatedResponse<>(items, total, page, pageSize);
    }
}
