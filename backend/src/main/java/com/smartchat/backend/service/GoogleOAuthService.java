/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartchat.backend.model.GoogleOAuthToken;
import com.smartchat.backend.repository.GoogleOAuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final GoogleOAuthTokenRepository tokenRepository;

    @Value("${google.client.id:}")
    private String clientId;

    @Value("${google.client.secret:}")
    private String clientSecret;

    @Value("${google.oauth.redirectUri:}")
    private String redirectUri;

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private MultiValueMap<String, String> toFormData(Map<String, String> map) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        map.forEach(formData::add);
        return formData;
    }

    /**
     * ✅ Save latest Google access token so we can later refresh or use for invite
     */
    @Transactional
    public void saveAccessToken(Long ownerUserId, String accessToken) {
        GoogleOAuthToken token = tokenRepository.findByOwnerUserId(ownerUserId)
                .orElseGet(GoogleOAuthToken::new);

        token.setOwnerUserId(ownerUserId);
        token.setAccessToken(accessToken);
        // We don’t know exact expiry here, so set a short default TTL (~1h)
        token.setExpiresAt(Instant.now().getEpochSecond() + 3600);
        token.setUpdatedAt(Instant.now());

        tokenRepository.save(token);
    }

    @Transactional
    public void exchangeCodeForTokens(Long ownerUserId, String code) {
        try {
            var formData = toFormData(Map.of(
                    "code", code,
                    "client_id", clientId,
                    "client_secret", clientSecret,
                    "redirect_uri", redirectUri,
                    "grant_type", "authorization_code"
            ));

            String resp = webClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = mapper.readTree(resp);
            String accessToken = node.path("access_token").asText(null);
            String refreshToken = node.path("refresh_token").asText(null);
            String tokenType = node.path("token_type").asText(null);
            long expiresIn = node.path("expires_in").asLong(0);
            long expiresAt = expiresIn > 0 ? Instant.now().getEpochSecond() + expiresIn : 0;
            String scope = node.path("scope").asText(null);

            GoogleOAuthToken t = tokenRepository.findByOwnerUserId(ownerUserId)
                    .orElseGet(GoogleOAuthToken::new);

            t.setOwnerUserId(ownerUserId);
            t.setAccessToken(accessToken);
            if (refreshToken != null && !refreshToken.isBlank()) {
                t.setRefreshToken(refreshToken);
            }
            t.setTokenType(tokenType);
            t.setExpiresAt(expiresAt);
            t.setScope(scope);
            t.setUpdatedAt(Instant.now());

            tokenRepository.save(t);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to exchange code for tokens", ex);
        }
    }

    /**
     * ✅ Return a valid Google access token for invites
     */
    public String getValidAccessToken(Long ownerUserId) {
        Optional<GoogleOAuthToken> opt = tokenRepository.findByOwnerUserId(ownerUserId);
        if (opt.isEmpty()) return null;
        GoogleOAuthToken t = opt.get();
        long now = Instant.now().getEpochSecond();

        if (t.getAccessToken() != null && t.getExpiresAt() != null && t.getExpiresAt() > now + 30) {
            return t.getAccessToken();
        }

        // Try refresh
        if (t.getRefreshToken() == null) return t.getAccessToken();
        try {
            var formData = toFormData(Map.of(
                    "client_id", clientId,
                    "client_secret", clientSecret,
                    "refresh_token", t.getRefreshToken(),
                    "grant_type", "refresh_token"
            ));

            String resp = webClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = mapper.readTree(resp);
            String accessToken = node.path("access_token").asText(null);
            long expiresIn = node.path("expires_in").asLong(0);
            long expiresAt = expiresIn > 0 ? Instant.now().getEpochSecond() + expiresIn : 0;

            if (accessToken != null) {
                t.setAccessToken(accessToken);
                t.setExpiresAt(expiresAt);
                t.setUpdatedAt(Instant.now());
                tokenRepository.save(t);
                return accessToken;
            } else {
                return null;
            }
        } catch (Exception ex) {
            return null;
        }
    }
}
