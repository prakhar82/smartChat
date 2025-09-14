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
import com.smartchat.backend.model.User;
import com.smartchat.backend.model.UserContact;
import com.smartchat.backend.repository.UserContactRepository;
import com.smartchat.backend.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Service responsible for fetching, caching, and syncing
 * Google contacts into the application database.
 */
@Service
@RequiredArgsConstructor
public class GoogleContactService {

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final PhoneNumberService phoneNumberService;
    private final UserRepository userRepository;
    private final UserContactRepository userContactRepository;
    private final MongoTemplate mongoTemplate;
    private final GoogleOAuthService oauthService;
    private final ContactService contactService;

    // ✅ Use generic RedisTemplate
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${google.client.id:}")
    private String googleClientId;

    @Value("${google.people.personFields:names,phoneNumbers,emailAddresses}")
    private String personFields;

    @Value("${google.people.pageSize:1000}")
    private int pageSize;

    @Value("${google.cache.ttl.minutes:10}")
    private int cacheTtlMinutes;

    private static final String TOKENINFO_ENDPOINT = "https://oauth2.googleapis.com/tokeninfo";
    private static final String PEOPLE_API_BASE = "https://people.googleapis.com/v1/people/me/connections";
    private static final String CACHE_KEY_PREFIX = "google_contacts:";

    /**
     * DTO for simplified Google contact info
     */
    @Data
    public static class GoogleContactDto {
        private String name;
        private List<String> phoneNumbers = new ArrayList<>();
        private Map<String, Object> raw;
    }

    /**
     * Fetch Google contacts with Redis caching
     */
    @Transactional
    public void fetchAndSync(Long ownerUserId, String accessToken) {
        String cacheKey = CACHE_KEY_PREFIX + ownerUserId;

        // ✅ Check cache
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof String cachedJson) {
            try {
                List<GoogleContactDto> cachedContacts = Arrays.asList(
                        mapper.readValue(cachedJson, GoogleContactDto[].class)
                );
                System.out.println("⚡ Using cached Google contacts for userId=" + ownerUserId);
                saveContactsToDb(ownerUserId, cachedContacts);
                return;
            } catch (Exception ignored) {
            }
        }

        // ✅ Validate token
        validateGoogleToken(accessToken);

        // ✅ Fetch from API
        List<GoogleContactDto> contacts = fetchFromGoogleApi(accessToken);

        // ✅ Save to DB
        saveContactsToDb(ownerUserId, contacts);

        // ✅ Save to Redis as JSON
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    mapper.writeValueAsString(contacts),
                    Duration.ofMinutes(cacheTtlMinutes)
            );
        } catch (Exception ignored) {
        }
    }

    private void validateGoogleToken(String accessToken) {
        if (googleClientId == null || googleClientId.isBlank()) return;

        try {
            String tokenInfo = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(TOKENINFO_ENDPOINT)
                            .queryParam("access_token", accessToken)
                            .build())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(),
                            resp -> Mono.error(new RuntimeException("Invalid Google token")))
                    .bodyToMono(String.class)
                    .block();

            JsonNode tokenInfoNode = mapper.readTree(tokenInfo);
            if (tokenInfoNode.has("aud")) {
                String aud = tokenInfoNode.get("aud").asText();
                if (!googleClientId.equals(aud)) {
                    throw new RuntimeException("Google token audience mismatch");
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Google token validation failed", ex);
        }
    }

    private List<GoogleContactDto> fetchFromGoogleApi(String accessToken) {
        List<GoogleContactDto> result = new ArrayList<>();
        String nextPageToken = null;

        do {
            final String pageTokenParam = nextPageToken;

            String responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(PEOPLE_API_BASE)
                            .queryParam("personFields", personFields)
                            .queryParam("pageSize", pageSize)
                            .queryParamIfPresent("pageToken", Optional.ofNullable(pageTokenParam))
                            .build())
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(),
                            resp -> Mono.error(new RuntimeException("Unauthorized or bad request from People API")))
                    .onStatus(status -> status.is5xxServerError(),
                            resp -> Mono.error(new RuntimeException("Server error from People API")))
                    .bodyToMono(String.class)
                    .block();

            try {
                JsonNode root = mapper.readTree(responseBody);
                if (root.has("connections")) {
                    for (JsonNode conn : root.get("connections")) {
                        GoogleContactDto dto = new GoogleContactDto();
                        dto.setRaw(mapper.convertValue(conn, Map.class));

                        if (conn.has("names") && conn.get("names").isArray() && conn.get("names").size() > 0) {
                            dto.setName(conn.get("names").get(0).get("displayName").asText(null));
                        }

                        if (conn.has("phoneNumbers") && conn.get("phoneNumbers").isArray()) {
                            for (JsonNode phone : conn.get("phoneNumbers")) {
                                if (phone.has("value")) {
                                    dto.getPhoneNumbers().add(phone.get("value").asText());
                                }
                            }
                        }

                        result.add(dto);
                    }
                }
                nextPageToken = root.has("nextPageToken") ? root.get("nextPageToken").asText(null) : null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse People API response", e);
            }
        } while (nextPageToken != null);

        return result;
    }

    private void saveContactsToDb(Long ownerUserId, List<GoogleContactDto> contacts) {
        Optional<User> ownerOpt = userRepository.findById(ownerUserId);
        if (ownerOpt.isEmpty() || ownerOpt.get().getMobileNormalized() == null) {
            throw new IllegalStateException("Owner user must have a normalized mobile number before syncing contacts");
        }
        String ownerMobileE164 = ownerOpt.get().getMobileNormalized();

        Map<String, String> normalizedToName = new LinkedHashMap<>();

        for (GoogleContactDto dto : contacts) {
            String name = dto.getName();

            for (String rawPhone : dto.getPhoneNumbers()) {
                String normalized = phoneNumberService.normalizeToE164(rawPhone, ownerMobileE164);
                if (normalized != null) {
                    normalizedToName.putIfAbsent(normalized, name);
                }
            }

            try {
                mongoTemplate.save(
                        Map.of("ownerUserId", ownerUserId, "raw", dto.getRaw(), "syncedAt", Instant.now()),
                        "google_contacts_raw"
                );
            } catch (Exception ignored) {
            }
        }

        List<UserContact> toSave = new ArrayList<>();
        for (var entry : normalizedToName.entrySet()) {
            String phone = entry.getKey();
            String name = entry.getValue();

            userContactRepository.findByOwnerUserIdAndPhoneNormalized(ownerUserId, phone)
                    .ifPresentOrElse(existing -> {
                        existing.setContactName(name != null ? name : existing.getContactName());
                        existing.setUpdatedAt(Instant.now());
                        toSave.add(existing);
                    }, () -> {
                        UserContact uc = new UserContact();
                        uc.setOwnerUserId(ownerUserId);
                        uc.setContactName(name);
                        uc.setPhoneNormalized(phone);
                        uc.setSource("google");
                        uc.setCreatedAt(Instant.now());
                        uc.setUpdatedAt(Instant.now());
                        toSave.add(uc);
                    });
        }

        if (!toSave.isEmpty()) {
            userContactRepository.saveAll(toSave);
        }

        try {
            contactService.evictMatchedCache(ownerUserId);
        } catch (Exception ignored) {
        }
    }
}
