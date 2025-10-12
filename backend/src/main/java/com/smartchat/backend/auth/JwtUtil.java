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
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * ==========================================================
 * ✅ JwtUtil
 * ----------------------------------------------------------
 * Centralized utility for:
 * - JWT access/refresh token generation
 * - Claim extraction
 * - Token validation & expiration handling
 * ----------------------------------------------------------
 * Now includes both "email" and "mobile" fields for flexibility.
 * ==========================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private static final String CLASS = "[JwtUtil]";
    private final JwtProperties jwtProperties;

    // ==========================================================
    // 🔹 Helper — Build Signing Key
    // ==========================================================
    private Key getSigningKey() {
        String secret = jwtProperties.getSecret();
        byte[] keyBytes;

        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(CLASS + " ❌ Secret key too short. Must be at least 256 bits (32 bytes)");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ==========================================================
    // 🔹 Token Generation
    // ==========================================================

    /**
     * Generates a short-lived access token containing userId, username,
     * email, mobile, and roles.
     */
    public String generateAccessToken(Long userId, String username, String email, String mobile, List<String> roles) {
        return buildToken(userId, username, email, mobile, roles, jwtProperties.getExpiration());
    }

    /**
     * Generates a long-lived refresh token.
     */
    public String generateRefreshToken(Long userId, String username, String email, String mobile) {
        return buildToken(userId, username, email, mobile, null, jwtProperties.getRefreshExpiration());
    }

    /**
     * Internal method to construct a JWT with provided data.
     */
    private String buildToken(Long userId, String username, String email, String mobile, List<String> roles, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        JwtBuilder builder = Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .claim("userId", userId)
                .claim("email", email)
                .claim("mobile", mobile)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256);

        if (roles != null && !roles.isEmpty()) {
            builder.claim("roles", roles);
        }

        String token = builder.compact();
        log.debug("{} ✅ Generated JWT (userId={}, exp={}, email={}, mobile={})", CLASS, userId, expiry, email, mobile);
        return token;
    }

    // ==========================================================
    // 🔹 Claim Extraction
    // ==========================================================
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    public String extractMobile(String token) {
        return extractClaim(token, claims -> claims.get("mobile", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> claims.get("roles", List.class));
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        try {
            Claims claims = extractAllClaims(token);
            return resolver.apply(claims);
        } catch (ExpiredJwtException e) {
            log.warn("{} ⚠️ Token expired at {}", CLASS, e.getClaims().getExpiration());
            throw e;
        } catch (JwtException e) {
            log.error("{} ❌ Invalid token → {}", CLASS, e.getMessage());
            throw e;
        }
    }

    public Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("{} ⚠️ Token expired for subject={}", CLASS, e.getClaims().getSubject());
            throw e;
        } catch (JwtException e) {
            log.error("{} ❌ Failed to parse JWT → {}", CLASS, e.getMessage());
            throw e;
        }
    }

    public String extractTokenFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }


    // ==========================================================
    // 🔹 Token Validation
    // ==========================================================
    public boolean isTokenValid(String token, String username) {
        try {
            final String tokenUsername = extractUsername(token);
            if (tokenUsername == null || !tokenUsername.equals(username)) {
                log.warn("{} ⚠️ Username mismatch: expected={}, found={}", CLASS, username, tokenUsername);
                return false;
            }

            if (isTokenExpired(token)) {
                log.warn("{} ⚠️ Token expired for user={}", CLASS, tokenUsername);
                return false;
            }

            log.debug("{} ✅ Token valid for user={}", CLASS, tokenUsername);
            return true;
        } catch (JwtException e) {
            log.error("{} ❌ Token validation failed → {}", CLASS, e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (JwtException e) {
            log.warn("{} ⚠️ Cannot determine expiration → {}", CLASS, e.getMessage());
            return true;
        }
    }
}
