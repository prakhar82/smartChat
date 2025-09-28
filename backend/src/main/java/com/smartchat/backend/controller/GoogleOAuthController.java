/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.auth.JwtUtil;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/oauth/google")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final GoogleOAuthService oauthService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthController.class);

    private static final String CACHE_PREFIX = "google:accessToken:";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    /**
     * Exchange OAuth authorization code for access + refresh tokens.
     */
    @PostMapping("/exchange")
    public ResponseEntity<?> exchangeCode(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader
    ) {
        if (!body.containsKey("code")) {
            log.warn("❌ [{}] Missing code in /exchange request", getClass().getSimpleName());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "code is required"));
        }

        try {
            Long ownerUserId = extractUserId(authHeader);
            String code = body.get("code");

            oauthService.exchangeCodeForTokens(ownerUserId, code);

            // Invalidate any cached token
            redisTemplate.delete(CACHE_PREFIX + ownerUserId);

            log.info("✅ [{}] Successfully exchanged Google OAuth code for user {}",
                    getClass().getSimpleName(), ownerUserId);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            log.error("❌ [{}] Failed to exchange Google OAuth code", getClass().getSimpleName(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Fetch a valid access token for logged-in user.
     * Uses Redis cache to avoid frequent DB lookups.
     */
    @GetMapping("/token")
    public ResponseEntity<?> getAccessToken(@RequestHeader("Authorization") String authHeader) {
        Long ownerUserId = extractUserId(authHeader);
        String cacheKey = CACHE_PREFIX + ownerUserId;

        // Try cache first
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof String token) {
            log.info("✅ [{}] Returning cached Google access token for user {}",
                    getClass().getSimpleName(), ownerUserId);
            return ResponseEntity.ok(Map.of("accessToken", token));
        }

        // Fallback to DB/service
        String token = oauthService.getValidAccessToken(ownerUserId);
        if (token == null) {
            log.warn("❌ [{}] No valid access token found for user {}",
                    getClass().getSimpleName(), ownerUserId);
            return ResponseEntity.notFound().build();
        }

        // Cache result for 50 minutes (Google token usually lasts 1 hour)
        Duration ttl = Duration.ofMinutes(50);
        Instant expiryAt = Instant.now().plus(ttl);
        redisTemplate.opsForValue().set(cacheKey, token, ttl);

        log.info("✅ [{}] Returning fresh Google access token for user {} (cached until {})",
                getClass().getSimpleName(),
                ownerUserId,
                FORMATTER.format(expiryAt));

        return ResponseEntity.ok(Map.of("accessToken", token));
    }

    // =======================
    // Helpers
    // =======================

    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        return userRepository.findByMobileNumber(username)
                .map(u -> u.getId())
                .orElseThrow(() -> new RuntimeException("User not found for token"));
    }
}
