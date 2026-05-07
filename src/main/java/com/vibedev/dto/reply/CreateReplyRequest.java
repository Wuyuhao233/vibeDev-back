package com.vibedev.dto.reply;

import jakarta.validation.constraints.NotBlank;

public record CreateReplyRequest(
        @NotBlank String content,
        String parentReplyId,
        @NotBlank String idempotencyKey
) {}
