package com.vibedev.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(
                "test-secret-key-that-is-long-enough-for-hmac-sha256-minimum-256-bits",
                900L,
                604800L,
                1800L,
                3600L);
    }

    @Test
    void shouldGenerateAndValidateAccessToken() {
        String token = jwtUtil.generateAccessToken(userId, "testuser", "user", 0);
        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));

        Claims claims = jwtUtil.parseToken(token);
        assertEquals(userId, jwtUtil.getUserId(claims));
        assertEquals("testuser", jwtUtil.getUsername(claims));
        assertEquals("user", jwtUtil.getRole(claims));
        assertEquals("access", jwtUtil.getTokenType(claims));
        assertEquals(0, jwtUtil.getTokenVersion(claims));
    }

    @Test
    void shouldGenerateAndValidateRefreshToken() {
        String token = jwtUtil.generateRefreshToken(userId, 1);
        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));

        Claims claims = jwtUtil.parseToken(token);
        assertEquals("refresh", jwtUtil.getTokenType(claims));
        assertEquals(1, jwtUtil.getTokenVersion(claims));
    }

    @Test
    void shouldGenerateAndValidateResetToken() {
        String token = jwtUtil.generateResetToken(userId);
        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));

        Claims claims = jwtUtil.parseToken(token);
        assertEquals("reset", jwtUtil.getTokenType(claims));
    }

    @Test
    void shouldRejectInvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid-token"));
        assertFalse(jwtUtil.validateToken(""));
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    void shouldRejectAccessTokenAsResetToken() {
        String accessToken = jwtUtil.generateAccessToken(userId, "testuser", "user", 0);
        Claims claims = jwtUtil.parseToken(accessToken);
        assertNotEquals("reset", jwtUtil.getTokenType(claims));
    }
}
