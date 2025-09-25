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

    @Data
    public static class GoogleContactDto {
        private String name;
        private List<String> phoneNumbers = new ArrayList<>(); // format: "label:value"
        private List<String> emails = new ArrayList<>();       // format: "label:value"
        private Map<String, Object> raw;
    }

    @Transactional
    public void fetchAndSync(Long ownerUserId, String accessToken) {
        validateGoogleToken(accessToken);

        List<GoogleContactDto> contacts = fetchFromGoogleApi(accessToken);
        saveContactsToDb(ownerUserId, contacts);

        try {
            redisTemplate.opsForValue().set(
                    CACHE_KEY_PREFIX + ownerUserId,
                    mapper.writeValueAsString(contacts),
                    Duration.ofMinutes(cacheTtlMinutes)
            );
        } catch (Exception ignored) {
        }

        oauthService.saveAccessToken(ownerUserId, accessToken);
    }

    private void validateGoogleToken(String accessToken) {
        if (googleClientId == null || googleClientId.isBlank()) return;

        try {
            String tokenInfo = webClient.get()
                    .uri(TOKENINFO_ENDPOINT + "?access_token=" + accessToken)
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
                    .uri(PEOPLE_API_BASE + "?personFields=" + personFields +
                            "&pageSize=" + pageSize +
                            (pageTokenParam != null ? "&pageToken=" + pageTokenParam : ""))
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            resp -> Mono.error(new RuntimeException("Google People API error")))
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
                                    String type = phone.has("type") ? phone.get("type").asText("mobile") : "mobile";
                                    dto.getPhoneNumbers().add(type + ":" + phone.get("value").asText());
                                }
                            }
                        }

                        if (conn.has("emailAddresses") && conn.get("emailAddresses").isArray()) {
                            for (JsonNode email : conn.get("emailAddresses")) {
                                if (email.has("value")) {
                                    String type = email.has("type") ? email.get("type").asText("home") : "home";
                                    dto.getEmails().add(type + ":" + email.get("value").asText());
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

        Map<String, UserContact> toUpsert = new LinkedHashMap<>();

        for (GoogleContactDto dto : contacts) {
            String name = dto.getName();

            for (String rawPhone : dto.getPhoneNumbers()) {
                String[] parts = rawPhone.split(":", 2);
                String label = parts[0];
                String number = parts.length > 1 ? parts[1] : rawPhone;

                String normalized = phoneNumberService.normalizeToE164(number, ownerMobileE164);
                if (normalized != null) {
                    String key = "phone:" + normalized;
                    UserContact uc = toUpsert.computeIfAbsent(key, k -> {
                        UserContact newUc = new UserContact();
                        newUc.setOwnerUserId(ownerUserId);
                        newUc.setPhoneNormalized(normalized);
                        newUc.setPhoneRaw(number);
                        newUc.setLabel(label);
                        newUc.setSource("google");
                        newUc.setCreatedAt(Instant.now());
                        return newUc;
                    });
                    uc.setContactName(name != null ? name : uc.getContactName());
                    uc.setUpdatedAt(Instant.now());
                }
            }

            for (String rawEmail : dto.getEmails()) {
                String[] parts = rawEmail.split(":", 2);
                String label = parts[0];
                String email = parts.length > 1 ? parts[1] : rawEmail;

                if (email != null && !email.isBlank()) {
                    String normalizedEmail = email.trim().toLowerCase();
                    String key = "email:" + normalizedEmail;
                    UserContact uc = toUpsert.computeIfAbsent(key, k -> {
                        UserContact newUc = new UserContact();
                        newUc.setOwnerUserId(ownerUserId);
                        newUc.setEmail(normalizedEmail);
                        newUc.setLabel(label);
                        newUc.setSource("google");
                        newUc.setCreatedAt(Instant.now());
                        return newUc;
                    });
                    uc.setContactName(name != null ? name : uc.getContactName());
                    uc.setUpdatedAt(Instant.now());
                }
            }
        }

        if (!toUpsert.isEmpty()) {
            List<UserContact> finalList = new ArrayList<>();
            for (UserContact uc : toUpsert.values()) {
                Optional<UserContact> existingOpt = Optional.empty();

                if (uc.getPhoneNormalized() != null) {
                    existingOpt = userContactRepository.findByOwnerUserIdAndPhoneNormalized(ownerUserId, uc.getPhoneNormalized());
                } else if (uc.getEmail() != null) {
                    existingOpt = userContactRepository.findByOwnerUserIdAndEmailIgnoreCase(ownerUserId, uc.getEmail());
                }

                if (existingOpt.isPresent()) {
                    UserContact existing = existingOpt.get();
                    existing.setContactName(uc.getContactName());
                    existing.setLabel(uc.getLabel());
                    existing.setUpdatedAt(Instant.now());
                    finalList.add(existing);
                } else {
                    finalList.add(uc);
                }
            }
            userContactRepository.saveAll(finalList);
        }

        try {
            contactService.evictMatchedCache(ownerUserId);
        } catch (Exception ignored) {
        }
    }
}
