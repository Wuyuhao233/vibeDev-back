package com.vibedev.dto.admin.sensitive;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchImportRequest(
        @NotNull @Size(min = 1, max = 500) List<AddSensitiveWordRequest> words
) {}
