/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartchat.common.contracts.AuthContract;
import com.smartchat.common.contracts.ChatContract;
import com.smartchat.common.dto.UserDTO;
import com.smartchat.contact.dto.ContactSyncRequest;
import com.smartchat.contact.dto.GoogleContactDto;
import com.smartchat.contact.model.Contact;
import com.smartchat.contact.repository.ContactRepository;
import com.smartchat.contact.service.cache.RedisContactCacheService;
import com.smartchat.contact.util.ContactUtil;
import com.smartchat.contact.util.GoogleApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * [GoogleContactService]
 * ------------------------------------------------------------
 * Handles full Google Contacts synchronization lifecycle:
 * ✅ Validates Google OAuth token
 * ✅ Fetches contacts from Google People API
 * ✅ Deduplicates, normalizes, and saves to database
 * ✅ Sorts contacts: with numbers (A→Z) first, without numbers (A→Z) last
 * ✅ Caches contact data in Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleContactService {

    private static final String CLASS = "[GoogleContactService]";

    private final ObjectMapper mapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder().build();

    private final AuthContract authContract;
    private final ChatContract chatContract;
    private final ContactRepository contactRepository;
    private final GoogleOAuthService oauthService;
    private final ContactService contactService;
    private final RedisContactCacheService redisCache;
    private final GoogleApiClient googleApiClient;

    @Value("${google.client.id:}")
    private String googleClientId;

    @Value("${google.people.pageSize:1000}")
    private int pageSize;

    @Value("${google.cache.ttl.minutes:10}")
    private int cacheTtlMinutes;

    private static final String TOKENINFO_ENDPOINT = "https://oauth2.googleapis.com/tokeninfo";

    // =====================================================
    // 🟢 MAIN SYNC FLOW
    // =====================================================
    @Transactional
    public void fetchAndSync(Long ownerUserId, String accessToken) {
        log.info("{} 🚀 Starting Google contact sync for userId={}", CLASS, ownerUserId);

        validateGoogleToken(accessToken);

        List<GoogleContactDto> contacts = googleApiClient.fetchContacts(accessToken, pageSize);
        log.info("{} 📥 Retrieved {} contacts from Google API for userId={}", CLASS, contacts.size(), ownerUserId);

        contacts = sortContactsAlphabetically(contacts);
        log.info("{} 🔡 Contacts sorted: {} with numbers, {} without numbers", CLASS,
                contacts.stream().filter(c -> !c.getPhoneNumbers().isEmpty()).count(),
                contacts.stream().filter(c -> c.getPhoneNumbers().isEmpty()).count()
        );

        saveContactsToDb(ownerUserId, contacts);

        try {
            redisCache.cacheRawContacts(ownerUserId, mapper.writeValueAsString(contacts), cacheTtlMinutes);
            log.info("{} 💾 Cached {} contacts in Redis (TTL={}m) for userId={}", CLASS,
                    contacts.size(), cacheTtlMinutes, ownerUserId);
        } catch (Exception e) {
            log.warn("{} ⚠️ Failed to cache Google contacts for userId={}: {}", CLASS, ownerUserId, e.getMessage());
        }

        oauthService.saveAccessToken(ownerUserId, accessToken);
        log.info("{} ✅ Google sync completed successfully for userId={}", CLASS, ownerUserId);
    }

    // =====================================================
    // 🔍 TOKEN VALIDATION
    // =====================================================
    private void validateGoogleToken(String accessToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            log.debug("{} ⚠️ Skipping token validation (google.client.id not configured)", CLASS);
            return;
        }

        try {
            String tokenInfo = webClient.get()
                    .uri(TOKENINFO_ENDPOINT + "?access_token=" + accessToken)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(),
                            resp -> Mono.error(new RuntimeException("Invalid Google access token")))
                    .bodyToMono(String.class)
                    .block();

            JsonNode tokenNode = mapper.readTree(tokenInfo);
            String aud = tokenNode.path("aud").asText(null);

            if (aud != null && !aud.equals(googleClientId)) {
                throw new RuntimeException("Google token audience mismatch");
            }

            log.debug("{} 🔑 Google token validated successfully for aud={}", CLASS, aud);
        } catch (Exception ex) {
            log.error("{} ❌ Google token validation failed: {}", CLASS, ex.getMessage());
            throw new RuntimeException("Google token validation failed", ex);
        }
    }

    // =====================================================
    // 💾 SAVE CONTACTS TO DATABASE (duplicate-safe)
    // =====================================================
    private void saveContactsToDb(Long ownerUserId, List<GoogleContactDto> contacts) {
        log.info("{} ▶ Preparing to save {} contacts for ownerUserId={}", CLASS, contacts.size(), ownerUserId);

        // 🧠 Get all SmartChat users via AuthContract
        List<UserDTO> allUsers = Optional.ofNullable(authContract.getAllUsers())
                .orElse(List.of());
        int saved = 0, skipped = 0, matched = 0;

        for (GoogleContactDto dto : contacts) {
            String name = dto.getName();

            // 🔹 Phones
            for (String rawPhone : dto.getPhoneNumbers()) {
                String[] parts = rawPhone.split(":", 2);
                String label = parts[0];
                String number = parts.length > 1 ? parts[1] : rawPhone;

                String normalized = ContactUtil.normalizePhone(number);
                if (normalized == null) continue;

                if (contactRepository.findByOwnerUserIdAndNormalizedPhone(ownerUserId, normalized).isPresent()) {
                    skipped++;
                    continue;
                }

                Contact contact = new Contact();
                contact.setOwnerUserId(ownerUserId);
                contact.setContactName(name);
                contact.setNormalizedPhone(normalized);
                contact.setPhones(List.of(new ContactSyncRequest.PhoneEntry(label, normalized, false)));
                contact.setEmails(new ArrayList<>());

                Optional<UserDTO> match = ContactUtil.matchPhoneToUser(normalized, allUsers);
                if (match.isPresent()) {
                    UserDTO user = match.get();
                    contact.setSmartChatUserId(Long.parseLong(user.getId()));
                    matched++;
                    if (user.getEmail() != null) {
                        contact.getEmails().add(new ContactSyncRequest.EmailEntry("account", user.getEmail()));
                    }
                }

                contactRepository.save(contact);
                saved++;
            }

            // 🔹 Emails
            for (String rawEmail : dto.getEmails()) {
                String[] parts = rawEmail.split(":", 2);
                String label = parts[0];
                String email = parts.length > 1 ? parts[1] : rawEmail;

                if (email == null || email.isBlank()) continue;
                String normalizedEmail = email.trim().toLowerCase();

                if (contactRepository.findByOwnerUserIdAndNormalizedEmail(ownerUserId, normalizedEmail).isPresent()) {
                    skipped++;
                    continue;
                }

                Contact contact = new Contact();
                contact.setOwnerUserId(ownerUserId);
                contact.setContactName(name);
                contact.setNormalizedEmail(normalizedEmail);
                contact.setEmails(List.of(new ContactSyncRequest.EmailEntry(label, normalizedEmail)));
                contact.setPhones(new ArrayList<>());

                Optional<UserDTO> match = allUsers.stream()
                        .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(normalizedEmail))
                        .findFirst();

                if (match.isPresent()) {
                    UserDTO user = match.get();
                    contact.setSmartChatUserId(Long.parseLong(user.getId()));
                    matched++;
                }

                contactRepository.save(contact);
                saved++;
            }
        }

        log.info("{} 📊 Sync summary for userId={}: saved={}, skipped={}, matched={}", CLASS, ownerUserId, saved, skipped, matched);

        try {
            redisCache.evictMatchedCache(ownerUserId);
            log.info("{} 🗑️ Evicted matched contact cache for userId={}", CLASS, ownerUserId);
        } catch (Exception e) {
            log.warn("{} ⚠️ Failed to evict Redis cache for userId={}: {}", CLASS, ownerUserId, e.getMessage());
        }
    }

    // =====================================================
    // 🔡 SORTING LOGIC
    // =====================================================
    private List<GoogleContactDto> sortContactsAlphabetically(List<GoogleContactDto> contacts) {
        List<GoogleContactDto> withNumbers = contacts.stream()
                .filter(c -> c.getPhoneNumbers() != null && !c.getPhoneNumbers().isEmpty())
                .sorted(Comparator.comparing(c -> Optional.ofNullable(c.getName()).orElse("").toLowerCase()))
                .collect(Collectors.toList());

        List<GoogleContactDto> withoutNumbers = contacts.stream()
                .filter(c -> c.getPhoneNumbers() == null || c.getPhoneNumbers().isEmpty())
                .sorted(Comparator.comparing(c -> Optional.ofNullable(c.getName()).orElse("").toLowerCase()))
                .collect(Collectors.toList());

        List<GoogleContactDto> sorted = new ArrayList<>();
        sorted.addAll(withNumbers);
        sorted.addAll(withoutNumbers);
        return sorted;
    }
}
