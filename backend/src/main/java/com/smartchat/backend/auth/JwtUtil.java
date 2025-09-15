/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.auth;

import com.smartchat.backend.config.auth.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtUtil {

    private final JwtProperties jwtProperties;

    private Key getSigningKey() {
        String secret = jwtProperties.getSecret();
        byte[] keyBytes;

        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 256 bits (32 bytes)");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    // =========================
    // Generate Tokens
    // =========================

    public String generateAccessToken(Long userId, String username, List<String> roles) {
        return buildToken(userId, username, roles, jwtProperties.getExpiration());
    }

    public String generateRefreshToken(Long userId, String username) {
        return buildToken(userId, username, null, jwtProperties.getRefreshExpiration());
    }

    private String buildToken(Long userId, String username, List<String> roles, long expirationMs) {
        JwtBuilder builder = Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .claim("userId", userId) // ✅ include userId in JWT
                .signWith(getSigningKey(), SignatureAlgorithm.HS256);

        if (roles != null && !roles.isEmpty()) {
            builder.claim("roles", roles);
        }

        return builder.compact();
    }

    // =========================
    // Extract Claims
    // =========================
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> claims.get("roles", List.class));
    }

    private <T> T extractClaim(String token, java.util.function.Function<Claims, T> claimsResolver) {
        try {
            Claims claims = extractAllClaims(token);
            return claimsResolver.apply(claims);
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            throw e;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // =========================
    // Validate Tokens
    // =========================
    public boolean isTokenValid(String token, String username) {
        try {
            final String tokenUsername = extractUsername(token);
            return tokenUsername.equals(username) && !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }
}
