package com.vibedev.dto.user;

public record NotificationPreference(
        String eventType,
        String channel
) {}