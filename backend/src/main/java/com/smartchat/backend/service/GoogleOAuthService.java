/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    private static final String AUTH_BASE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    // ======== STEP 1: Build Google Auth URL ========
    public String buildAuthUrl() {
        String url = AUTH_BASE_URL +
                "?client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&response_type=code" +
                "&scope=" + "https://www.googleapis.com/auth/contacts.readonly" +
                "&access_type=offline" +   // ensures refresh token
                "&prompt=consent";         // always ask for consent (to get refresh token again)

        log.info("🌐 Generated Google OAuth URL: {}", url);
        return url;
    }

    // ======== STEP 2a: Exchange code during callback (no userId yet) ========
    public String exchangeCodeForTokens(String code) {
        log.info("📥 Exchanging Google code for tokens...");

        Map<String, Object> tokenResponse = doTokenRequest(code);

        // You may want to return accessToken only here
        return (String) tokenResponse.get("access_token");
    }

    // ======== STEP 2b: Exchange code with userId (after login/registration) ========
    public void exchangeCodeForTokens(Long userId, String code) {
        log.info("📥 Exchanging Google code for userId={}...", userId);

        Map<String, Object> tokenResponse = doTokenRequest(code);

        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");
        Integer expiresIn = (Integer) tokenResponse.get("expires_in");

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found: " + userId);
        }

        User user = userOpt.get();
        user.setGoogleAccessToken(accessToken);
        user.setGoogleRefreshToken(refreshToken);
        user.setGoogleTokenExpiry(Instant.now().plusSeconds(expiresIn != null ? expiresIn : 3600));
        userRepository.save(user);

        log.info("✅ Stored Google tokens for user {}", userId);
    }

    // ======== STEP 3: Get valid access token (refresh if expired) ========
    public String getValidAccessToken(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("❌ No user found with id={}", userId);
            return null;
        }

        User user = userOpt.get();
        if (user.getGoogleAccessToken() == null) {
            log.warn("❌ No Google access token stored for user {}", userId);
            return null;
        }

        // Check expiry
        if (user.getGoogleTokenExpiry() == null || Instant.now().isBefore(user.getGoogleTokenExpiry())) {
            return user.getGoogleAccessToken();
        }

        // Expired -> refresh using refresh_token
        if (user.getGoogleRefreshToken() == null) {
            log.error("❌ Cannot refresh token because refresh_token is missing for user {}", userId);
            return null;
        }

        log.info("♻️ Refreshing Google access token for user {}", userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("refresh_token", user.getGoogleRefreshToken());
        params.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(TOKEN_URL, request, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("❌ Failed to refresh Google token: {}", response);
            return null;
        }

        Map<String, Object> tokenResponse = response.getBody();
        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            log.error("❌ Invalid Google refresh response: {}", response);
            return null;
        }

        String newAccessToken = (String) tokenResponse.get("access_token");
        Integer expiresIn = (Integer) tokenResponse.get("expires_in");

        user.setGoogleAccessToken(newAccessToken);
        user.setGoogleTokenExpiry(Instant.now().plusSeconds(expiresIn != null ? expiresIn : 3600));
        userRepository.save(user);

        log.info("✅ Refreshed Google access token for user {}", userId);

        return newAccessToken;
    }

    // ======== Helper: Perform token exchange request ========
    private Map<String, Object> doTokenRequest(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(TOKEN_URL, request, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("❌ Google token request failed: {}", response);
            throw new RuntimeException("Failed to exchange code for tokens");
        }

        Map<String, Object> body = response.getBody();
        log.debug("🔑 Google token response: {}", body);
        return body;
    }

    public void saveAccessToken(Long userId, String accessToken) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setGoogleAccessToken(accessToken);
            user.setGoogleTokenExpiry(Instant.now().plusSeconds(3600)); // assume 1 hour
            userRepository.save(user);
            log.info("✅ Saved Google access token for user {}", userId);
        });
    }

}
