/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.service;

import com.smartchat.common.contracts.AuthContract;
import com.smartchat.common.dto.UserDTO;
import com.smartchat.contact.cache.CachedOAuthToken;
import com.smartchat.contact.dto.GoogleTokenDTO;
import com.smartchat.contact.repository.cache.CachedOAuthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private static final String CLASS = "[GoogleOAuthService]";

    private final AuthContract authContract;
    private final CachedOAuthRepository cachedOAuthRepository;

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    private static final String AUTH_BASE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_PATH = "/token";
    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://oauth2.googleapis.com")
            .build();

    // ---------------------------------------------------------
    public String buildAuthUrlWithState(Long userId) {
        Objects.requireNonNull(clientId, "google.oauth.client-id must be set");
        Objects.requireNonNull(redirectUri, "google.oauth.redirect-uri must be set");

        var scope = "https://www.googleapis.com/auth/contacts.readonly";
        // state contains userId (consider HMAC if you need integrity)
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String encodedScope = URLEncoder.encode("https://www.googleapis.com/auth/contacts.readonly", StandardCharsets.UTF_8);
        return String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&access_type=offline&prompt=consent&state=%d",
                AUTH_BASE_URL, clientId, encodedRedirect, encodedScope, userId);

    }

    // ---------------------------------------------------------
    public GoogleTokenDTO exchangeCodeForTokens(String code) {
        log.info("{} ▶ Exchanging Google code for tokens", CLASS);
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("code", code);
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("redirect_uri", redirectUri);
            form.add("grant_type", "authorization_code");

            Map<String, Object> resp = webClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            if (resp == null || !resp.containsKey("access_token")) {
                log.warn("{} ⚠ Empty or invalid token response for code={}", CLASS, code);
                return null;
            }

            String accessToken = String.valueOf(resp.get("access_token"));
            String refreshToken = resp.getOrDefault("refresh_token", "").toString();
            long expiresIn = Long.parseLong(String.valueOf(resp.getOrDefault("expires_in", 3600)));

            log.info("{} ✅ Received access token (expires in {}s) — refresh present={}", CLASS, expiresIn, !refreshToken.isBlank());
            return new GoogleTokenDTO(accessToken, refreshToken, expiresIn);
        } catch (Exception e) {
            log.error("{} ❌ Token exchange failed → {}", CLASS, e.getMessage(), e);
            return null;
        }
    }

    // ---------------------------------------------------------
    @Transactional
    public boolean exchangeCodeForTokens(String code, String userIdStr) {
        try {
            var tokenData = exchangeCodeForTokens(code);
            if (tokenData == null || tokenData.getAccessToken() == null) {
                log.warn("{} ⚠ Token exchange returned no access token for userId={}", CLASS, userIdStr);
                return false;
            }

            UserDTO user = authContract.getUserById(userIdStr);
            if (user == null) {
                log.warn("{} ❌ No SmartChat user found for userId={}", CLASS, userIdStr);
                return false;
            }

            cacheToken(Long.parseLong(user.getId()), tokenData.getAccessToken(), tokenData.getRefreshToken(), tokenData.getExpiresIn());
            log.info("{} ✅ Stored Google tokens for userId={}", CLASS, userIdStr);
            return true;
        } catch (Exception e) {
            log.error("{} ❌ exchangeCodeForTokens failed for userId={} → {}", CLASS, userIdStr, e.getMessage(), e);
            return false;
        }
    }

    // ---------------------------------------------------------
    public String getValidAccessToken(Long userId) {
        log.debug("{} 🔄 Fetching valid access token for userId={}", CLASS, userId);
        try {
            String cacheKey = "google:" + userId;
            Optional<CachedOAuthToken> optional = cachedOAuthRepository.findById(cacheKey);
            CachedOAuthToken cached = optional.orElse(null);

            if (cached != null && Instant.now().isBefore(Instant.ofEpochMilli(cached.getExpiresAt()))) {
                log.info("{} ⚡ Cache hit for userId={}", CLASS, userId);
                return cached.getAccessToken();
            }

            if (cached != null && cached.getRefreshToken() != null && !cached.getRefreshToken().isBlank()) {
                log.info("{} 🔁 Access token expired/missing — attempting refresh for userId={}", CLASS, userId);
                GoogleTokenDTO refreshed = refreshAccessToken(cached.getRefreshToken());
                if (refreshed != null && refreshed.getAccessToken() != null) {
                    String refreshToStore = (refreshed.getRefreshToken() != null && !refreshed.getRefreshToken().isBlank())
                            ? refreshed.getRefreshToken()
                            : cached.getRefreshToken();

                    cacheToken(userId, refreshed.getAccessToken(), refreshToStore, refreshed.getExpiresIn());
                    return refreshed.getAccessToken();
                } else {
                    log.warn("{} ⚠ Refresh attempt failed for userId={}", CLASS, userId);
                }
            }

            log.warn("{} ⚠ No valid token found for userId={}, refresh not available/failed", CLASS, userId);
            return null;
        } catch (Exception e) {
            log.error("{} ❌ getValidAccessToken failed for userId={} → {}", CLASS, userId, e.getMessage(), e);
            return null;
        }
    }

    // ---------------------------------------------------------
    private GoogleTokenDTO refreshAccessToken(String refreshToken) {
        log.info("{} ▶ Refreshing access token using refresh_token", CLASS);
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("refresh_token", refreshToken);
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("grant_type", "refresh_token");

            Map<String, Object> resp = webClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            if (resp == null || !resp.containsKey("access_token")) {
                log.warn("{} ⚠️ Refresh endpoint returned no access_token", CLASS);
                return null;
            }

            String accessToken = String.valueOf(resp.get("access_token"));
            long expiresIn = Long.parseLong(String.valueOf(resp.getOrDefault("expires_in", 3600)));
            String newRefresh = resp.getOrDefault("refresh_token", "").toString();

            log.info("{} ✅ Refreshed access token (expires in {}s)", CLASS, expiresIn);
            return new GoogleTokenDTO(accessToken, newRefresh, expiresIn);
        } catch (Exception e) {
            log.error("{} ❌ Failed to refresh access token → {}", CLASS, e.getMessage(), e);
            return null;
        }
    }

    // ---------------------------------------------------------
    @Transactional
    public void cacheToken(Long userId, String accessToken, String refreshToken, long expiresInSeconds) {
        try {
            long expiresAt = Instant.now().plusSeconds(expiresInSeconds).toEpochMilli();
            CachedOAuthToken entry = CachedOAuthToken.builder()
                    .id("google:" + userId)
                    .ownerUserId(userId)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresAt(expiresAt)
                    .build();
            cachedOAuthRepository.save(entry);
            log.debug("{} 🧠 Cached token for userId={}", CLASS, userId);
        } catch (Exception e) {
            log.error("{} ❌ Failed to cache token for userId={} → {}", CLASS, userId, e.getMessage(), e);
        }
    }

    public void saveAccessToken(Long userId, String accessToken) {
        cacheToken(userId, accessToken, "", 3600L);
    }

    // ---------------------------------------------------------
    public Long resolveUserIdFromPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            log.debug("{} ⚠ Principal null/blank", CLASS);
            return null;
        }
        try {
            Long resolved = authContract.resolveUserIdFromPrincipal(principal);
            if (resolved != null) {
                log.info("{} ✅ Resolved principal='{}' -> userId={}", CLASS, principal, resolved);
                return resolved;
            }
        } catch (Exception e) {
            log.error("{} ❌ Error resolving principal='{}' → {}", CLASS, principal, e.getMessage(), e);
        }
        return null;
    }
}
