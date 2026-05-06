package com.vibedev.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenTtl;
    private final long refreshTokenTtl;
    private final long resetTokenTtl;
    private final long verifyEmailTokenTtl;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl}") long accessTokenTtl,
            @Value("${app.jwt.refresh-token-ttl}") long refreshTokenTtl,
            @Value("${app.jwt.reset-token-ttl}") long resetTokenTtl,
            @Value("${app.jwt.verify-email-token-ttl}") long verifyEmailTokenTtl) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(secret.getBytes())));
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.resetTokenTtl = resetTokenTtl;
        this.verifyEmailTokenTtl = verifyEmailTokenTtl;
    }

    public String generateAccessToken(UUID userId, String username, String role, int tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", userId.toString());
        claims.put("username", username);
        claims.put("role", role);
        claims.put("token_version", tokenVersion);
        claims.put("type", "access");
        return buildToken(claims, accessTokenTtl);
    }

    public String generateRefreshToken(UUID userId, int tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", userId.toString());
        claims.put("token_version", tokenVersion);
        claims.put("type", "refresh");
        return buildToken(claims, refreshTokenTtl);
    }

    public String generateResetToken(UUID userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", userId.toString());
        claims.put("type", "reset");
        return buildToken(claims, resetTokenTtl);
    }

    public String generateVerifyEmailToken(UUID userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", userId.toString());
        claims.put("email", email);
        claims.put("type", "verify_email");
        return buildToken(claims, verifyEmailTokenTtl);
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.get("user_id", String.class));
    }

    public String getTokenType(Claims claims) {
        return claims.get("type", String.class);
    }

    public int getTokenVersion(Claims claims) {
        return claims.get("token_version", Integer.class);
    }

    public String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    private String buildToken(Map<String, Object> claims, long ttl) {
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl * 1000))
                .signWith(key)
                .compact();
    }
}
