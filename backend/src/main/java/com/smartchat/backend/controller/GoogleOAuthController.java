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
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final GoogleOAuthService oauthService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthController.class);
    private static final String CLASS = "[GoogleOAuthController]";

    private static final String CACHE_PREFIX = "google:accessToken:";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    /**
     * Start OAuth flow: redirect user to Google's consent screen.
     */
    @GetMapping("/init")
    public void initOAuth(HttpServletResponse response) throws IOException {
        String redirectUrl = oauthService.buildAuthUrl();
        log.info("{} 🌐 Redirecting user to Google OAuth: {}", CLASS, redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    /**
     * Callback from Google with authorization code.
     */
    @GetMapping("/callback")
    public void callback(@RequestParam String code, HttpServletResponse response) throws IOException {
        log.info("{} 📥 Received Google OAuth callback with code={}", CLASS, code);

        // Exchange for token
        String googleToken = oauthService.exchangeCodeForTokens(code);

        // ✅ Redirect to Angular with snake_case param
        String frontendUrl = "http://localhost/chats?access_token=" +
                URLEncoder.encode(googleToken, StandardCharsets.UTF_8);

        log.info("{} 🔁 Redirecting back to frontend: {}", CLASS, frontendUrl);
        response.sendRedirect(frontendUrl);
    }

    /**
     * Fetch a valid access token for logged-in user.
     */
    @GetMapping("/token")
    public ResponseEntity<?> getAccessToken(@RequestHeader("Authorization") String authHeader) {
        Long ownerUserId = extractUserId(authHeader);
        String cacheKey = CACHE_PREFIX + ownerUserId;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof String token) {
            log.info("{} ✅ Returning cached Google access token for user {}", CLASS, ownerUserId);
            return ResponseEntity.ok(Map.of("access_token", token));
        }

        String token = oauthService.getValidAccessToken(ownerUserId);
        if (token == null) {
            log.warn("{} ❌ No valid access token found for user {}", CLASS, ownerUserId);
            return ResponseEntity.notFound().build();
        }

        Duration ttl = Duration.ofMinutes(50);
        Instant expiryAt = Instant.now().plus(ttl);
        redisTemplate.opsForValue().set(cacheKey, token, ttl);

        log.info("{} ✅ Returning fresh Google access token for user {} (cached until {})",
                CLASS, ownerUserId, FORMATTER.format(expiryAt));

        return ResponseEntity.ok(Map.of("access_token", token));
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
