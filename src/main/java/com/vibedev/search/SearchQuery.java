package com.vibedev.search;

public record SearchQuery(
        String keyword,
        SearchScope scope,
        String boardId,
        int page,
        int limit) {

    public SearchQuery {
        if (limit < 1) limit = 20;
        if (limit > 50) limit = 50;
        if (page < 1) page = 1;
    }
}
