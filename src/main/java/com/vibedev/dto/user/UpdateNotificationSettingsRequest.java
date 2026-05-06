package com.vibedev.dto.user;

import java.util.List;

public record UpdateNotificationSettingsRequest(
        List<NotificationPreference> preferences
) {}