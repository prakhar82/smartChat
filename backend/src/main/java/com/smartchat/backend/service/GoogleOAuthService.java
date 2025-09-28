/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartchat.backend.model.GoogleOAuthToken;
import com.smartchat.backend.repository.GoogleOAuthTokenRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {

    private final GoogleOAuthTokenRepository tokenRepo;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    /**
     * Returns a valid access token for the given user.
     * Refreshes the token if expired.
     */
    public String getValidAccessToken(Long userId) {
        Optional<GoogleOAuthToken> optToken = tokenRepo.findByOwnerUserId(userId);
        if (optToken.isEmpty()) {
            log.warn("[GoogleOAuthService] ⚠ No Google OAuth token found for userId={}", userId);
            return null;
        }

        GoogleOAuthToken token = optToken.get();

        // ✅ Still valid
        if (token.getExpiresAt() != null && token.getExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            log.debug("[GoogleOAuthService] ✅ Using cached access token for userId={} (expires at {})",
                    userId, token.getExpiresAt());
            return token.getAccessToken();
        }

        // ❌ Need refresh
        if (token.getRefreshToken() == null) {
            log.error("[GoogleOAuthService] ❌ No refresh token stored for userId={}, cannot refresh", userId);
            return null;
        }

        try {
            log.info("[GoogleOAuthService] 🔄 Refreshing Google access token for userId={}", userId);

            String url = "https://oauth2.googleapis.com/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = "client_id=" + clientId +
                    "&client_secret=" + clientSecret +
                    "&grant_type=refresh_token" +
                    "&refresh_token=" + token.getRefreshToken();

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> res = response.getBody();

                String newAccessToken = (String) res.get("access_token");
                int expiresIn = ((Number) res.get("expires_in")).intValue();

                token.setAccessToken(newAccessToken);
                token.setExpiresAt(Instant.now().plusSeconds(expiresIn));
                token.setUpdatedAt(Instant.now());

                tokenRepo.save(token);

                log.info("[GoogleOAuthService] ✅ Successfully refreshed access token for userId={} (expires in {}s)",
                        userId, expiresIn);
                return newAccessToken;
            } else {
                log.error("[GoogleOAuthService] ❌ Failed to refresh token for userId={}, response={}",
                        userId, response);
            }
        } catch (Exception e) {
            log.error("[GoogleOAuthService] ❌ Exception while refreshing token for userId=" + userId, e);
        }

        return null;
    }

    public void saveAccessToken(Long ownerUserId, String accessToken) {
        GoogleOAuthToken token = tokenRepo.findByOwnerUserId(ownerUserId)
                .orElseGet(() -> GoogleOAuthToken.builder()
                        .ownerUserId(ownerUserId)
                        .createdAt(Instant.now())
                        .build()
                );

        token.setAccessToken(accessToken);
        token.setUpdatedAt(Instant.now());

        // ⚡ Default expiry 1 hour if not provided
        if (token.getExpiresAt() == null) {
            token.setExpiresAt(Instant.now().plusSeconds(3600));
        }

        tokenRepo.save(token);
        log.info("[GoogleOAuthService] 💾 Saved access token for userId={} (expires at {})",
                ownerUserId, token.getExpiresAt());
    }

    public void exchangeCodeForTokens(Long ownerUserId, String code) {
        String tokenUrl = "https://oauth2.googleapis.com/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "code=" + code +
                "&client_id=" + clientId +
                "&client_secret=" + clientSecret +
                "&redirect_uri=" + redirectUri +
                "&grant_type=authorization_code";

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<TokenResponse> response = restTemplate.exchange(
                tokenUrl, HttpMethod.POST, request, TokenResponse.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("[GoogleOAuthService] ❌ Failed to exchange code for tokens (userId={}) response={}",
                    ownerUserId, response);
            throw new RuntimeException("Failed to exchange code for tokens");
        }

        TokenResponse tokenResponse = response.getBody();

        GoogleOAuthToken token = tokenRepo.findByOwnerUserId(ownerUserId)
                .orElseGet(() -> GoogleOAuthToken.builder()
                        .ownerUserId(ownerUserId)
                        .createdAt(Instant.now())
                        .build());

        token.setAccessToken(tokenResponse.getAccessToken());
        token.setRefreshToken(tokenResponse.getRefreshToken()); // might be null if already granted
        token.setTokenType(tokenResponse.getTokenType());
        token.setScope(tokenResponse.getScope());
        token.setUpdatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(tokenResponse.getExpiresIn()));

        tokenRepo.save(token);

        log.info("[GoogleOAuthService] 🔑 Stored new OAuth tokens for userId={} (expires in {}s, scope={})",
                ownerUserId, tokenResponse.getExpiresIn(), tokenResponse.getScope());
    }

    @Data
    static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("refresh_token")
        private String refreshToken;

        @JsonProperty("expires_in")
        private long expiresIn;

        @JsonProperty("token_type")
        private String tokenType;

        private String scope;
    }
}
