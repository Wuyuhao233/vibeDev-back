package com.vibedev.dto.user;

import java.util.List;

public record NotificationSettingsResponse(
        List<NotificationPreference> preferences,
        List<String> mandatoryEvents
) {}