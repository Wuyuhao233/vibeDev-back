package com.vibedev.dto.admin.settings;

import java.util.Map;

public record SystemSettingsData(
        Map<String, String> settings
) {}
