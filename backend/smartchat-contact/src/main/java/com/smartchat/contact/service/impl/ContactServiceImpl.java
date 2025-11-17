/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.service.impl;

import com.smartchat.common.contracts.AuthContract;
import com.smartchat.common.dto.MatchedContactResponse;
import com.smartchat.common.dto.UserDTO;
import com.smartchat.contact.dto.ContactSyncRequest;
import com.smartchat.contact.model.Contact;
import com.smartchat.contact.repository.ContactRepository;
import com.smartchat.contact.service.ContactService;
import com.smartchat.contact.service.cache.RedisContactCacheService;
import com.smartchat.contact.util.ContactUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * [ContactServiceImpl]
 * ------------------------------------------------------------
 * Handles all contact synchronization and matching operations:
 * ✅ Sync contacts from devices
 * ✅ Match contacts with SmartChat users via AuthContract
 * ✅ Cache matched results in Redis
 * ✅ Refresh and evict caches safely
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private static final String CLASS = "[ContactServiceImpl]";

    private final ContactRepository contactRepository;
    private final AuthContract authContract;
    private final RedisContactCacheService redisCache;

    // ==========================================================
    // 🔄 SYNC CONTACTS FROM DEVICE
    // ==========================================================
    @Override
    @Transactional
    public void syncContacts(ContactSyncRequest request) {
        Long ownerUserId = request.getOwnerUserId();
        if (ownerUserId == null) {
            throw new IllegalArgumentException("Owner userId is required for syncing contacts");
        }

        log.info("{} ▶ Starting contact sync for ownerUserId={} ({} contacts)", CLASS, ownerUserId, request.getContacts().size());
        int saved = 0, skipped = 0;

        List<UserDTO> allUsers = Optional.ofNullable(authContract.getAllUsers()).orElse(List.of());

        for (ContactSyncRequest.Contect dto : request.getContacts()) {
            Contact contact = new Contact();
            contact.setOwnerUserId(ownerUserId);
            contact.setContactName(dto.getContactName());
            contact.setPhones(dto.getPhones());
            contact.setEmails(dto.getEmails());

            // ✅ Normalize and check phone uniqueness
            if (dto.getPhones() != null && !dto.getPhones().isEmpty()) {
                String rawPhone = dto.getPhones().get(0).getValue();
                String normalized = ContactUtil.normalizePhone(rawPhone);
                contact.setNormalizedPhone(normalized);

                if (normalized != null &&
                        contactRepository.findByOwnerUserIdAndNormalizedPhone(ownerUserId, normalized).isPresent()) {
                    skipped++;
                    log.debug("{} ⚠️ Duplicate phone={} skipped for userId={}", CLASS, normalized, ownerUserId);
                    continue;
                }
            }

            // ✅ Normalize and check email uniqueness
            if (dto.getEmails() != null && !dto.getEmails().isEmpty()) {
                String rawEmail = dto.getEmails().get(0).getValue();
                if (rawEmail != null) {
                    String normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
                    contact.setNormalizedEmail(normalizedEmail);

                    if (contactRepository.findByOwnerUserIdAndNormalizedEmail(ownerUserId, normalizedEmail).isPresent()) {
                        skipped++;
                        log.debug("{} ⚠️ Duplicate email={} skipped for userId={}", CLASS, normalizedEmail, ownerUserId);
                        continue;
                    }
                }
            }

            // ✅ Try to match contact to existing SmartChat user
            Optional<UserDTO> match = Optional.empty();
            if (contact.getNormalizedPhone() != null) {
                match = ContactUtil.matchPhoneToUser(contact.getNormalizedPhone(), allUsers);
            } else if (contact.getNormalizedEmail() != null) {
                match = allUsers.stream()
                        .filter(u -> u.getEmail() != null &&
                                u.getEmail().equalsIgnoreCase(contact.getNormalizedEmail()))
                        .findFirst();
            }

            match.ifPresent(user -> {
                contact.setSmartChatUserId(Long.parseLong(user.getId()));
                log.debug("{} 📌 Contact '{}' matched to SmartChat userId={}", CLASS, contact.getContactName(), user.getId());
            });

            contactRepository.save(contact);
            saved++;
            log.trace("{} 💾 Saved contact='{}' for userId={}", CLASS, contact.getContactName(), ownerUserId);
        }

        log.info("{} ✅ Sync completed for userId={} → saved={}, skipped={}", CLASS, ownerUserId, saved, skipped);
        refreshMatchedCache(ownerUserId);
    }

    // ==========================================================
    // 👥 FETCH MATCHED CONTACTS (Cache → DB)
    // ==========================================================
    @Override
    @Transactional(readOnly = true)
    public List<MatchedContactResponse> getMatchedContacts(Long ownerUserId) {
        log.info("{} ▶ Fetching matched contacts for userId={}", CLASS, ownerUserId);

        // 1️⃣ Try cache
        List<MatchedContactResponse> cached = redisCache.getMatchedContacts(ownerUserId);
        if (cached != null && !cached.isEmpty()) {
            log.info("{} ⚡ Returning {} contacts from Redis cache for userId={}", CLASS, cached.size(), ownerUserId);
            return cached;
        }

        // 2️⃣ Cache miss — build from DB
        List<MatchedContactResponse> fromDb = fetchFromDatabase(ownerUserId);
        List<MatchedContactResponse> sorted = ContactUtil.sortContacts(fromDb);

        if (!sorted.isEmpty()) {
            redisCache.cacheMatchedContacts(ownerUserId, sorted);
            log.info("{} 💾 Cached {} matched contacts into Redis for userId={}", CLASS, sorted.size(), ownerUserId);
        } else {
            log.info("{} ⚠️ No matched contacts found for userId={}", CLASS, ownerUserId);
        }

        return sorted;
    }

    // ==========================================================
    // 🧹 CACHE MANAGEMENT
    // ==========================================================
    @Override
    public void evictMatchedCache(Long userId) {
        redisCache.evictMatchedCache(userId);
        log.info("{} 🗑️ Evicted matched contacts cache for userId={}", CLASS, userId);
    }

    @Override
    public boolean userHasContacts(Long userId) {
        boolean has = contactRepository.existsByOwnerUserId(userId);
        log.debug("{} 🔍 userId={} hasContacts={}", CLASS, userId, has);
        return has;
    }

    @Override
    public void refreshMatchedCache(Long userId) {
        log.info("{} 🔄 Refreshing matched cache for userId={}", CLASS, userId);
        redisCache.evictMatchedCache(userId);

        List<MatchedContactResponse> responses = fetchFromDatabase(userId);
        if (responses.isEmpty()) {
            log.info("{} ⚠️ No contacts found for userId={}, cache not refreshed", CLASS, userId);
            return;
        }

        redisCache.cacheMatchedContacts(userId, responses);
        log.info("{} ✅ Refreshed matched cache for userId={}, total={} contacts", CLASS, userId, responses.size());
    }

    // ==========================================================
    // 🧩 DB → DTO MAPPER
    // ==========================================================
    private List<MatchedContactResponse> fetchFromDatabase(Long ownerUserId) {
        List<Contact> contacts = contactRepository.findByOwnerUserId(ownerUserId);
        if (contacts.isEmpty()) {
            log.info("{} ⚠️ No contacts in DB for userId={}", CLASS, ownerUserId);
            return List.of();
        }

        List<UserDTO> allUsers = Optional.ofNullable(authContract.getAllUsers()).orElse(List.of());
        int matchedCount = 0;

        List<MatchedContactResponse> responses = new ArrayList<>();
        for (Contact contact : contacts) {
            Long matchedUserId = null;
            boolean matched = false;
            UserDTO matchedUser = null;

            List<MatchedContactResponse.PhoneEntry> phoneEntries = new ArrayList<>();
            if (contact.getPhones() != null) {
                for (ContactSyncRequest.PhoneEntry phone : contact.getPhones()) {
                    if (phone == null || phone.getValue() == null) continue;
                    Optional<UserDTO> userOpt = ContactUtil.matchPhoneToUser(phone.getValue(), allUsers);
                    boolean isRegistered = userOpt.isPresent();

                    if (isRegistered && matchedUser == null) {
                        matchedUser = userOpt.get();
                        matchedUserId = Long.parseLong(matchedUser.getId());
                        matched = true;
                        matchedCount++;
                        log.debug("{} 📞 Phone '{}' matched to userId={}", CLASS, phone.getValue(), matchedUserId);
                    }

                    phoneEntries.add(new MatchedContactResponse.PhoneEntry(
                            phone.getLabel(), phone.getValue(), isRegistered
                    ));
                }
            }

            List<MatchedContactResponse.EmailEntry> emailEntries = new ArrayList<>();
            if (contact.getEmails() != null) {
                contact.getEmails().forEach(e ->
                        emailEntries.add(new MatchedContactResponse.EmailEntry(e.getLabel(), e.getValue()))
                );
            }

            if (matchedUser != null && matchedUser.getEmail() != null) {
                UserDTO finalMatchedUser = matchedUser;
                boolean alreadyPresent = emailEntries.stream()
                        .anyMatch(e -> e.getValue().equalsIgnoreCase(finalMatchedUser.getEmail()));
                if (!alreadyPresent) {
                    emailEntries.add(new MatchedContactResponse.EmailEntry("account", matchedUser.getEmail()));
                    log.debug("{} 📧 Added matched user email={} to contactId={}", CLASS, matchedUser.getEmail(), contact.getId());
                }
            }

            MatchedContactResponse dto = MatchedContactResponse.builder()
                    .contactId(String.valueOf(contact.getId()))
                    .contactName(contact.getContactName())
                    .phones(phoneEntries)
                    .emails(emailEntries)
                    .matched(matched)
                    .canInvite(!matched)
                    .matchedUserId(matchedUserId)
                    .isOnline(false)
                    .build();

            responses.add(dto);
        }

        log.info("{} 📊 Built {} matched contacts for userId={}, matched={}", CLASS, responses.size(), ownerUserId, matchedCount);
        return responses;
    }
}
