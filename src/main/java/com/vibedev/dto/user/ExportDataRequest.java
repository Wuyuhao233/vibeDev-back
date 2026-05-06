package com.vibedev.dto.user;

public record ExportDataRequest(String scope) {
    public ExportDataRequest {
        if (scope == null || scope.isBlank()) {
            scope = "all";
        }
    }
}
