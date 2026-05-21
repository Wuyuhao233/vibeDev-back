package com.vibedev.dto.admin.sensitive;

public record UpdateSensitiveWordRequest(
        String word,
        String matchType,
        String category
) {}
