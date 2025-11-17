/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.security.jwt;

import com.smartchat.common.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private static final String CLASS = "[JwtUtil]";
    private final JwtProperties jwtProperties;

    private static final long CLOCK_SKEW_MS = 30_000;

    private volatile Key cachedKey;
    private volatile String cachedSecretRaw;

    // ==========================================================
    // 🔐 Key Handling
    // ==========================================================
    private synchronized Key buildAndCacheKey(String secret) {
        if (cachedKey != null && Objects.equals(cachedSecretRaw, secret)) return cachedKey;

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32)
            throw new IllegalArgumentException(CLASS + " ❌ Secret key too short (<256 bits)");

        cachedKey = Keys.hmacShaKeyFor(keyBytes);
        cachedSecretRaw = secret;
        return cachedKey;
    }

    private Key getSigningKey() {
        return buildAndCacheKey(Optional.ofNullable(jwtProperties.getSecret())
                .orElseThrow(() -> new IllegalStateException(CLASS + " ❌ JWT secret not configured")));
    }

    // ==========================================================
    // 🪄 Token Generation
    // ==========================================================
    public String generateAccessToken(Long userId, String username, String email, String mobile, List<String> roles) {
        return buildToken(userId, username, email, mobile, roles, jwtProperties.getExpiration());
    }

    public String generateRefreshToken(Long userId, String username, String email, String mobile) {
        return buildToken(userId, username, email, mobile, null, jwtProperties.getRefreshExpiration());
    }

    private String buildToken(Long userId, String username, String email, String mobile, List<String> roles, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        JwtBuilder builder = Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(expiry)
                .claim("username", username)
                .claim("email", email)
                .claim("mobile", mobile)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256);

        if (roles != null && !roles.isEmpty())
            builder.claim("roles", roles);

        String token = builder.compact();
        log.trace("{} ✅ JWT generated for userId={} exp={}", CLASS, userId, expiry);
        return token;
    }

    // ==========================================================
    // 🧩 Extraction
    // ==========================================================
    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .setAllowedClockSkewSeconds(CLOCK_SKEW_MS / 1000)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long extractUserId(String token) {
        try {
            return Long.parseLong(extractClaim(token, Claims::getSubject));
        } catch (Exception e) {
            log.debug("{} ⚠️ Failed to extract userId: {}", CLASS, e.getMessage());
            return null;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, c -> c.get("username", String.class));
    }

    public List<String> extractRoles(String token) {
        Object val = extractAllClaims(token).get("roles");
        if (val instanceof List<?>) {
            return ((List<?>) val).stream().map(String::valueOf).toList();
        } else if (val instanceof String s) {
            return Arrays.asList(s.split(","));
        }
        return Collections.emptyList();
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // ==========================================================
    // ✅ Validation
    // ==========================================================
    public boolean isTokenExpired(String token) {
        try {
            Date expiry = extractExpiration(token);
            return expiry.before(new Date(System.currentTimeMillis() - CLOCK_SKEW_MS));
        } catch (JwtException e) {
            log.warn("{} ⚠️ Unable to read token expiry: {}", CLASS, e.getMessage());
            return true;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (JwtException e) {
            log.warn("{} ❌ Invalid token: {}", CLASS, e.getMessage());
            return false;
        }
    }
}
