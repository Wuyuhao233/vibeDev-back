package com.vibedev.dto.mute;

public record MuteRequest(
    String duration,
    String reason,
    String boardId
) {}
