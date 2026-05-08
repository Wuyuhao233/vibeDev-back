package com.vibedev.dto.health;

public record HealthResponse(String status, HealthChecks checks) {

    public record HealthChecks(String db, String redis, String mail) {}
}
