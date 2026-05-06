package com.vibedev.dto.user;

import java.time.Instant;

public record DeactivateAccountResponse(
        Instant deactivatedAt,
        Instant recoveryDeadline
) {}
