package com.vibedev.dto.post;

public record ShareCardResponse(
        String title,
        String description,
        String coverUrl,
        String authorName,
        String url
) {}
