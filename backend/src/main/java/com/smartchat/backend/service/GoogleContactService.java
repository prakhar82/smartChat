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
import com.smartchat.backend.dto.ContactSyncRequest;
import com.smartchat.backend.model.Contact;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.ContactRepository;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.util.ContactUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleContactService {

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
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
        log.info("[GoogleContactService] 🚀 Starting Google sync for userId={}", ownerUserId);

        validateGoogleToken(accessToken);

        List<GoogleContactDto> contacts = fetchFromGoogleApi(accessToken);
        log.info("[GoogleContactService] 📥 Google API returned {} contacts for userId={}", contacts.size(), ownerUserId);

        saveContactsToDb(ownerUserId, contacts);

        try {
            redisTemplate.opsForValue().set(
                    CACHE_KEY_PREFIX + ownerUserId,
                    mapper.writeValueAsString(contacts),
                    Duration.ofMinutes(cacheTtlMinutes)
            );
            log.info("[GoogleContactService] 💾 Cached {} contacts in Redis for userId={}", contacts.size(), ownerUserId);
        } catch (Exception e) {
            log.warn("[GoogleContactService] ⚠️ Failed to cache Google contacts in Redis for userId={}: {}", ownerUserId, e.getMessage());
        }

        oauthService.saveAccessToken(ownerUserId, accessToken);
        log.info("[GoogleContactService] ✅ Google sync complete for userId={}", ownerUserId);
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
            log.debug("[GoogleContactService] 🔑 Google token validated successfully");
        } catch (Exception ex) {
            log.error("[GoogleContactService] ❌ Google token validation failed: {}", ex.getMessage());
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

                        if (conn.has("phoneNumbers")) {
                            for (JsonNode phone : conn.get("phoneNumbers")) {
                                String raw = phone.path("value").asText(null);
                                if (raw != null) {
                                    String type = phone.has("type") ? phone.get("type").asText("mobile") : "mobile";
                                    dto.getPhoneNumbers().add(type + ":" + raw);
                                }
                            }
                        }

                        if (conn.has("emailAddresses")) {
                            for (JsonNode email : conn.get("emailAddresses")) {
                                String raw = email.path("value").asText(null);
                                if (raw != null) {
                                    String type = email.has("type") ? email.get("type").asText("home") : "home";
                                    dto.getEmails().add(type + ":" + raw);
                                }
                            }
                        }

                        result.add(dto);
                    }
                }
                nextPageToken = root.has("nextPageToken") ? root.get("nextPageToken").asText(null) : null;
            } catch (Exception e) {
                log.error("[GoogleContactService] ❌ Failed to parse People API response: {}", e.getMessage());
                throw new RuntimeException("Failed to parse People API response", e);
            }
        } while (nextPageToken != null);

        return result;
    }

    private void saveContactsToDb(Long ownerUserId, List<GoogleContactDto> contacts) {
        List<User> allUsers = userRepository.findAll();

        Map<String, Contact> toUpsert = new LinkedHashMap<>();
        int skipped = 0;
        int saved = 0;
        AtomicInteger matched = new AtomicInteger();

        for (GoogleContactDto dto : contacts) {
            String name = dto.getName();

            // Phones
            for (String rawPhone : dto.getPhoneNumbers()) {
                String[] parts = rawPhone.split(":", 2);
                String label = parts[0];
                String number = parts.length > 1 ? parts[1] : rawPhone;

                String normalized = ContactUtil.normalizePhone(number);
                if (normalized != null) {
                    String key = "phone:" + normalized;
                    if (toUpsert.containsKey(key)) {
                        skipped++;
                        continue;
                    }
                    Contact contact = new Contact();
                    contact.setOwnerUserId(ownerUserId);
                    contact.setContactName(name);
                    contact.setPhones(List.of(new ContactSyncRequest.PhoneEntry(label, normalized, false)));
                    contact.setEmails(new ArrayList<>());
                    contact.setSmartChatUserId(null);

                    // Enrich if registered
                    Optional<User> match = ContactUtil.matchPhoneToUser(normalized, allUsers);
                    match.ifPresent(user -> {
                        contact.setSmartChatUserId(user.getId());
                        if (user.getEmail() != null) {
                            contact.getEmails().add(new ContactSyncRequest.EmailEntry("account", user.getEmail()));
                        }
                        matched.getAndIncrement();
                    });

                    toUpsert.put(key, contact);
                }
            }

            // Emails
            for (String rawEmail : dto.getEmails()) {
                String[] parts = rawEmail.split(":", 2);
                String label = parts[0];
                String email = parts.length > 1 ? parts[1] : rawEmail;

                if (email != null && !email.isBlank()) {
                    String normalizedEmail = email.trim().toLowerCase();
                    String key = "email:" + normalizedEmail;
                    if (toUpsert.containsKey(key)) {
                        skipped++;
                        continue;
                    }
                    Contact contact = new Contact();
                    contact.setOwnerUserId(ownerUserId);
                    contact.setContactName(name);
                    contact.setEmails(List.of(new ContactSyncRequest.EmailEntry(label, normalizedEmail)));
                    contact.setPhones(new ArrayList<>());
                    contact.setSmartChatUserId(null);

                    // Enrich if registered
                    Optional<User> match = allUsers.stream()
                            .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(normalizedEmail))
                            .findFirst();
                    match.ifPresent(user -> {
                        contact.setSmartChatUserId(user.getId());
                        matched.getAndIncrement();
                    });

                    toUpsert.put(key, contact);
                }
            }
        }

        if (!toUpsert.isEmpty()) {
            contactRepository.saveAll(toUpsert.values());
            saved = toUpsert.size();
        }

        log.info("[GoogleContactService] 📊 Sync summary for userId={}: fetched={}, skipped={}, saved={}, matched={}",
                ownerUserId, contacts.size(), skipped, saved, matched);

        try {
            contactService.evictMatchedCache(ownerUserId);
        } catch (Exception ignored) {
            log.warn("[GoogleContactService] ⚠️ Failed to evict cache after saving contacts for userId={}", ownerUserId);
        }
    }
}
