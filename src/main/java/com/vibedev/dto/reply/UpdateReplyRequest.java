package com.vibedev.dto.reply;

import jakarta.validation.constraints.NotBlank;

public record UpdateReplyRequest(
        @NotBlank String content,
        int version
) {}
