package com.vibedev.dto.search;

import java.util.List;

public record SearchSuggestResponse(
        List<String> suggestions) {
}
