package com.vibedev.dto.search;

import java.util.List;

public record SearchSuggestionResponse(
        List<String> hotSearches) {
}
