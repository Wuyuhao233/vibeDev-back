package com.vibedev.dto.user;

import java.time.Instant;

public record CasBindingInfo(
        boolean isBound,
        String casUsername,
        Instant boundAt
) {
    public static CasBindingInfo notBound() {
        return new CasBindingInfo(false, null, null);
    }

    public static CasBindingInfo bound(String casUsername, Instant boundAt) {
        return new CasBindingInfo(true, casUsername, boundAt);
    }
}
