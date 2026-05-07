package com.vibedev.dto.sensitive;

import java.util.List;

public record SensitiveCheckResponse(
    boolean hasSensitive,
    List<MatchItem> matches
) {
    public record MatchItem(String word, List<Integer> positions) {}
}
