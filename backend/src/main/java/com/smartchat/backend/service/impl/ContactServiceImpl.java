/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service.impl;

import com.smartchat.backend.dto.ContactSyncRequest;
import com.smartchat.backend.dto.MatchedContactResponse;
import com.smartchat.backend.model.Contact;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.jpa.ContactRepository;
import com.smartchat.backend.repository.jpa.UserRepository;
import com.smartchat.backend.service.ContactService;
import com.smartchat.backend.service.RedisContactCacheService;
import com.smartchat.backend.util.ContactUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final RedisContactCacheService redisCache;

    @Override
    @Transactional
    public void syncContacts(ContactSyncRequest request) {
        Long ownerUserId = request.getOwnerUserId();
        if (ownerUserId == null) {
            throw new IllegalArgumentException("Owner userId is required for syncing contacts");
        }

        int saved = 0, skipped = 0;

        for (ContactSyncRequest.Contect dto : request.getContacts()) {
            Contact contact = new Contact();
            contact.setOwnerUserId(ownerUserId);
            contact.setContactName(dto.getContactName());
            contact.setPhones(dto.getPhones());
            contact.setEmails(dto.getEmails());

            // ✅ Normalize and set phone/email for uniqueness
            if (dto.getPhones() != null && !dto.getPhones().isEmpty()) {
                String rawPhone = dto.getPhones().get(0).getValue();
                String normalized = ContactUtil.normalizePhone(rawPhone);
                contact.setNormalizedPhone(normalized);

                if (normalized != null &&
                        contactRepository.findByOwnerUserIdAndNormalizedPhone(ownerUserId, normalized).isPresent()) {
                    skipped++;
                    log.debug("[ContactServiceImpl] ⚠️ Duplicate phone={} skipped for userId={}", normalized, ownerUserId);
                    continue;
                }
            }

            if (dto.getEmails() != null && !dto.getEmails().isEmpty()) {
                String rawEmail = dto.getEmails().get(0).getValue();
                if (rawEmail != null) {
                    String normalizedEmail = rawEmail.trim().toLowerCase();
                    contact.setNormalizedEmail(normalizedEmail);

                    if (contactRepository.findByOwnerUserIdAndNormalizedEmail(ownerUserId, normalizedEmail).isPresent()) {
                        skipped++;
                        log.debug("[ContactServiceImpl] ⚠️ Duplicate email={} skipped for userId={}", normalizedEmail, ownerUserId);
                        continue;
                    }
                }
            }

            contactRepository.save(contact);
            saved++;
            log.info("[ContactServiceImpl] ✅ Saved contact={} for userId={}", contact.getContactName(), ownerUserId);
        }

        log.info("[ContactServiceImpl] 📊 Sync summary for userId={}: saved={}, skipped={}", ownerUserId, saved, skipped);

        refreshMatchedCache(ownerUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchedContactResponse> getMatchedContacts(Long ownerUserId) {

        List<MatchedContactResponse> cached = redisCache.getMatchedContacts(ownerUserId);

        // ✅ Detect and clear stale cache
        if (cached != null && cached.isEmpty()) {
            log.warn("[ContactServiceImpl] ⚠️ Empty Redis cache detected for userId={} — evicting and rebuilding", ownerUserId);
            redisCache.evictMatchedCache(ownerUserId);
            cached = null;
        }

        // ✅ Valid cache hit
        if (cached != null && !cached.isEmpty()) {
            log.info("[ContactServiceImpl] ✅ Returning {} contacts from Redis cache for userId={}", cached.size(), ownerUserId);
            return cached;
        }

        // 🚀 Cache miss → fetch from DB
        List<MatchedContactResponse> fromDb = fetchFromDatabase(ownerUserId);
        List<MatchedContactResponse> sortedDb = ContactUtil.sortContacts(fromDb);

        if (!sortedDb.isEmpty()) {
            redisCache.cacheMatchedContacts(ownerUserId, sortedDb);
            log.info("[ContactServiceImpl] 💾 Cached {} matched contacts into Redis for userId={}", sortedDb.size(), ownerUserId);
        } else {
            log.info("[ContactServiceImpl] ⚠️ No matched contacts found for userId={}", ownerUserId);
        }

        return sortedDb;
    }


    @Override
    public void evictMatchedCache(Long userId) {
        redisCache.evictMatchedCache(userId);
        log.info("[ContactServiceImpl] 🗑️ Evicted matched contacts cache for userId={}", userId);
    }

    @Override
    public boolean userHasContacts(Long userId) {
        boolean has = !contactRepository.findByOwnerUserId(userId).isEmpty();
        log.debug("[ContactServiceImpl] 🔍 userId={} hasContacts={}", userId, has);
        return has;
    }

    @Override
    public void refreshMatchedCache(Long userId) {
        redisCache.evictMatchedCache(userId);

        List<MatchedContactResponse> responses = fetchFromDatabase(userId);
        if (responses.isEmpty()) {
            log.info("[ContactServiceImpl] ⚠️ No contacts found for userId={}, cache not refreshed", userId);
            return;
        }

        redisCache.cacheMatchedContacts(userId, responses);
        log.info("[ContactServiceImpl] 🔄 Refreshed matched cache for userId={}, total={} contacts cached",
                userId, responses.size());
    }

    /**
     * Build matched contacts enriched with registered User info.
     */
    private List<MatchedContactResponse> fetchFromDatabase(Long ownerUserId) {
        List<Contact> contacts = contactRepository.findByOwnerUserId(ownerUserId);
        if (contacts.isEmpty()) {
            log.info("[ContactServiceImpl] ⚠️ No contacts in DB for userId={}", ownerUserId);
            return List.of();
        }

        List<User> allUsers = userRepository.findAll();
        int matchedCount = 0;

        List<MatchedContactResponse> responses = new ArrayList<>();
        for (Contact contact : contacts) {
            boolean matched = false;
            Long matchedUserId = null;
            User matchedUser = null;

            // Build phone entries
            List<MatchedContactResponse.PhoneEntry> phoneEntries = new ArrayList<>();
            if (contact.getPhones() != null) {
                for (ContactSyncRequest.PhoneEntry phone : contact.getPhones()) {
                    if (phone == null || phone.getValue() == null) continue;
                    Optional<User> userOpt = ContactUtil.matchPhoneToUser(phone.getValue(), allUsers);
                    boolean isRegistered = userOpt.isPresent();

                    if (isRegistered && matchedUser == null) {
                        matched = true;
                        matchedUser = userOpt.get();
                        matchedUserId = matchedUser.getId();
                        matchedCount++;
                        log.debug("[ContactServiceImpl] 📌 Phone {} matched to userId={}", phone.getValue(), matchedUserId);
                    }

                    phoneEntries.add(new MatchedContactResponse.PhoneEntry(
                            phone.getLabel(),
                            phone.getValue(),
                            isRegistered
                    ));
                }
            }

            // Build email entries
            List<MatchedContactResponse.EmailEntry> emailEntries = new ArrayList<>();
            if (contact.getEmails() != null) {
                emailEntries.addAll(
                        contact.getEmails().stream()
                                .map(e -> new MatchedContactResponse.EmailEntry(e.getLabel(), e.getValue()))
                                .toList()
                );
            }

            // ✅ If matched user found, enrich with their account email
            if (matchedUser != null && matchedUser.getEmail() != null) {
                User finalMatchedUser = matchedUser;
                boolean alreadyPresent = emailEntries.stream()
                        .anyMatch(e -> e.getValue().equalsIgnoreCase(finalMatchedUser.getEmail()));
                if (!alreadyPresent) {
                    emailEntries.add(new MatchedContactResponse.EmailEntry("account", matchedUser.getEmail()));
                    log.debug("[ContactServiceImpl] 📧 Added matched user's email={} to contactId={}",
                            matchedUser.getEmail(), contact.getId());
                }
            }

            MatchedContactResponse dto = new MatchedContactResponse(
                    String.valueOf(contact.getId()),
                    contact.getContactName(),
                    phoneEntries,
                    emailEntries,
                    matched,
                    !matched, // ✅ canInvite = true only if not registered
                    matchedUserId
            );
            responses.add(dto);
        }

        log.info("[ContactServiceImpl] 📊 Matched contacts build complete for userId={}: total={}, matched={}",
                ownerUserId, responses.size(), matchedCount);

        return responses;
    }
}
