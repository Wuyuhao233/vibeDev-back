package com.vibedev.search;

import com.vibedev.dto.search.SearchResponse;

public interface SearchEngine {

    SearchResponse search(SearchQuery query);

    default boolean supportsChineseSegmentation() {
        return false;
    }
}
