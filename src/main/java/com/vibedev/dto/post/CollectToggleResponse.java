package com.vibedev.dto.post;

public record CollectToggleResponse(
        boolean collected,
        int newCount
) {}
