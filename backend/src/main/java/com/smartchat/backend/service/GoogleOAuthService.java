/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.smartchat.backend.dto.GoogleTokenDTO;
import com.smartchat.backend.model.User;
import com.smartchat.backend.model.cache.CachedOAuthToken;
import com.smartchat.backend.repository.jpa.UserRepository;
import com.smartchat.backend.repository.redis.CachedOAuthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles Google OAuth integration for SmartChat.
 * This service exchanges OAuth codes, stores access tokens,
 * refreshes expired tokens, and caches valid ones in Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final UserRepository userRepository;
    private final CachedOAuthRepository cachedOAuthRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    private static final String AUTH_BASE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    /**
     * Production mode — ensures real API calls instead of mocks
     */
    private static final boolean MOCK_MODE = false;

    private static final String CLASS = "[GoogleOAuthService]";

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://oauth2.googleapis.com")
            .build();

    // ==========================================================
    // STEP 1: Exchange authorization code for access/refresh tokens
    // ==========================================================
    public GoogleTokenDTO exchangeCodeForTokens(String code) {
        log.info("{} 🔄 Exchanging OAuth code for tokens", CLASS);

        try {
            if (MOCK_MODE) {
                log.warn("{} ⚙️ MOCK MODE ENABLED — returning simulated token", CLASS);
                return new GoogleTokenDTO(
                        "ya29.mocked_access_token_" + code.substring(0, Math.min(6, code.length())),
                        "1//mocked_refresh_token",
                        3600L
                );
            }

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("code", code);
            requestBody.put("client_id", clientId);
            requestBody.put("client_secret", clientSecret);
            requestBody.put("redirect_uri", redirectUri);
            requestBody.put("grant_type", "authorization_code");

            Map<String, Object> tokenResponse = webClient.post()
                    .uri("/token")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                log.warn("{} ⚠️ Empty or invalid token response for code={}", CLASS, code);
                return null;
            }

            String accessToken = (String) tokenResponse.get("access_token");
            String refreshToken = (String) tokenResponse.getOrDefault("refresh_token", "");
            long expiresIn = Long.parseLong(tokenResponse.getOrDefault("expires_in", 3600).toString());

            log.info("{} ✅ Received Google access token (expires in {}s)", CLASS, expiresIn);
            return new GoogleTokenDTO(accessToken, refreshToken, expiresIn);

        } catch (Exception e) {
            log.error("{} ❌ Exception during token exchange: {}", CLASS, e.getMessage(), e);
            return null;
        }
    }

    // ==========================================================
    // STEP 2: Exchange code and persist tokens for a user
    // ==========================================================
    public void exchangeCodeForTokens(String code, Long userId) {
        log.info("{} 🔄 Processing OAuth code for userId={}", CLASS, userId);

        GoogleTokenDTO tokenData = exchangeCodeForTokens(code);

        if (tokenData == null || tokenData.getAccessToken() == null) {
            log.warn("{} ❌ No token received for userId={}", CLASS, userId);
            return;
        }

        userRepository.findById(userId).ifPresentOrElse(user -> {
            user.setGoogleAccessToken(tokenData.getAccessToken());
            user.setGoogleRefreshToken(tokenData.getRefreshToken());
            user.setGoogleTokenExpiry(Instant.now().plusSeconds(tokenData.getExpiresIn()));
            userRepository.save(user);
            cacheToken(user);
            log.info("{} ✅ Stored new Google tokens for userId={}", CLASS, userId);
        }, () -> log.warn("{} ⚠️ User not found for userId={}", CLASS, userId));
    }

    // ==========================================================
    // STEP 3: Build Google authorization URL with user-specific state
    // ==========================================================
    public String buildAuthUrlWithState(Long userId) {
        log.debug("{} 🌐 Building auth URL for userId={}", CLASS, userId);

        return String.format(
                "%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&access_type=offline&prompt=consent&state=%d",
                AUTH_BASE_URL,
                clientId,
                redirectUri,
                "https://www.googleapis.com/auth/contacts.readonly",
                userId
        );
    }

    // ==========================================================
    // STEP 4: Resolve SmartChat user ID from principal string
    // ==========================================================
    public Long resolveUserIdFromPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            log.warn("{} ⚠️ Principal is null or blank", CLASS);
            return null;
        }

        log.debug("{} 🔍 Resolving user from principal='{}'", CLASS, principal);

        // Try by mobile number
        var byMobile = userRepository.findByMobileNumber(principal);
        if (byMobile.isPresent()) {
            log.debug("{} ✅ Found user by mobile number", CLASS);
            return byMobile.get().getId();
        }

        // Try numeric userId
        try {
            Long userId = Long.parseLong(principal);
            if (userRepository.existsById(userId)) {
                log.debug("{} ✅ Found user by numeric ID", CLASS);
                return userId;
            }
        } catch (NumberFormatException ignored) {
        }

        // Try by email
        var byEmail = userRepository.findByEmail(principal);
        if (byEmail.isPresent()) {
            log.debug("{} ✅ Found user by email", CLASS);
            return byEmail.get().getId();
        }

        log.warn("{} ⚠️ No user found for principal={}", CLASS, principal);
        return null;
    }

    // ==========================================================
    // STEP 5: Get valid access token (cache → DB → refresh)
    // ==========================================================
    public String getValidAccessToken(Long userId) {
        log.debug("{} 🔄 Fetching valid access token for userId={}", CLASS, userId);

        // 1️⃣ Try cache first
        CachedOAuthToken cached = cachedOAuthRepository.findById("google:" + userId).orElse(null);
        if (cached != null && Instant.now().isBefore(Instant.ofEpochMilli(cached.getExpiresAt()))) {
            log.info("{} ⚡ Cache hit: valid token found for userId={}", CLASS, userId);
            return cached.getAccessToken();
        }

        // 2️⃣ Fetch from DB
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("{} ❌ No user found with userId={}", CLASS, userId);
            return null;
        }

        User user = userOpt.get();

        if (user.getGoogleAccessToken() == null) {
            log.warn("{} ⚠️ No access token stored for userId={}", CLASS, userId);
            return null;
        }

        // 3️⃣ If token not expired, re-cache it
        if (user.getGoogleTokenExpiry() != null && Instant.now().isBefore(user.getGoogleTokenExpiry())) {
            log.debug("{} ♻️ DB token still valid — recaching for userId={}", CLASS, userId);
            cacheToken(user);
            return user.getGoogleAccessToken();
        }

        // 4️⃣ Otherwise refresh using refresh token
        return refreshAccessToken(user);
    }

    // ==========================================================
    // STEP 6: Refresh expired Google token using refresh_token
    // ==========================================================
    private String refreshAccessToken(User user) {
        log.info("{} ♻️ Refreshing access token for userId={}", CLASS, user.getId());

        if (user.getGoogleRefreshToken() == null) {
            log.error("{} ❌ Cannot refresh — missing refresh token for userId={}", CLASS, user.getId());
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("refresh_token", user.getGoogleRefreshToken());
            params.add("grant_type", "refresh_token");

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(TOKEN_URL, new HttpEntity<>(params, headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("{} ❌ Invalid response from Google during refresh", CLASS);
                return null;
            }

            Map<String, Object> body = response.getBody();
            String newAccessToken = (String) body.get("access_token");
            Number expiresIn = (Number) body.getOrDefault("expires_in", 3600);

            user.setGoogleAccessToken(newAccessToken);
            user.setGoogleTokenExpiry(Instant.now().plusSeconds(expiresIn.longValue()));
            userRepository.save(user);
            cacheToken(user);

            log.info("{} ✅ Successfully refreshed Google token for userId={}", CLASS, user.getId());
            return newAccessToken;

        } catch (Exception e) {
            log.error("{} ❌ Token refresh failed for userId={}: {}", CLASS, user.getId(), e.getMessage(), e);
            return null;
        }
    }

    // ==========================================================
    // STEP 7: Save and cache new tokens
    // ==========================================================
    @Transactional
    public void saveAccessToken(Long userId, String accessToken) {
        userRepository.findById(userId).ifPresentOrElse(user -> {
            user.setGoogleAccessToken(accessToken);
            user.setGoogleTokenExpiry(Instant.now().plusSeconds(3600));
            userRepository.save(user);
            cacheToken(user);
            log.info("{} 💾 Saved Google access token for userId={}", CLASS, userId);
        }, () -> log.warn("{} ⚠️ Tried to save token but user not found (userId={})", CLASS, userId));
    }

    // ==========================================================
    // Helper: Cache token in Redis
    // ==========================================================
    private void cacheToken(User user) {
        cachedOAuthRepository.save(
                CachedOAuthToken.builder()
                        .id("google:" + user.getId())
                        .ownerUserId(user.getId())
                        .accessToken(user.getGoogleAccessToken())
                        .expiresAt(user.getGoogleTokenExpiry() != null
                                ? user.getGoogleTokenExpiry().toEpochMilli()
                                : Instant.now().plusSeconds(3600).toEpochMilli())
                        .build()
        );
        log.debug("{} 🧠 Cached token for userId={}", CLASS, user.getId());
    }
}
