package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.health.HealthResponse;
import com.vibedev.dto.health.HealthResponse.HealthChecks;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    public HealthController(DataSource dataSource, RedisConnectionFactory redisConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping
    public ApiResponse<HealthResponse> health() {
        var checks = new HealthChecks(checkDb(), checkRedis(), "not_configured");
        var status = "up".equals(checks.db()) && "up".equals(checks.redis()) ? "ok" : "degraded";
        return ApiResponse.ok(new HealthResponse(status, checks));
    }

    private String checkDb() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2) ? "up" : "down";
        } catch (Exception e) {
            return "down";
        }
    }

    private String checkRedis() {
        try {
            var conn = redisConnectionFactory.getConnection();
            conn.ping();
            conn.close();
            return "up";
        } catch (Exception e) {
            return "down";
        }
    }
}
