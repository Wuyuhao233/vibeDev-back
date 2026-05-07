package com.vibedev.dto.admin.settings;

import jakarta.validation.constraints.NotBlank;

public record UpdateSettingRequest(
        @NotBlank String configValue
) {}
