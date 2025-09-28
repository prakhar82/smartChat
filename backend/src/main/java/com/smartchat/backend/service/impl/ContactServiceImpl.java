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
import com.smartchat.backend.repository.ContactRepository;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.ContactService;
import com.smartchat.backend.util.ContactUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "contacts:matched:";

    @Override
    @Transactional
    public void syncContacts(ContactSyncRequest request) {
        Long ownerUserId = request.getOwnerUserId();
        if (ownerUserId == null) {
            throw new IllegalArgumentException("Owner userId is required for syncing contacts");
        }

        List<Contact> entities = request.toEntities(ownerUserId);
        contactRepository.saveAll(entities);

        log.info("[ContactServiceImpl] 📥 Synced {} contacts for userId={}", entities.size(), ownerUserId);

        refreshMatchedCache(ownerUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchedContactResponse> getMatchedContacts(Long ownerUserId) {
        String key = CACHE_PREFIX + ownerUserId;

        // ✅ Try Redis cache first
        Set<Object> cached = redisTemplate.opsForSet().members(key);
        if (cached != null && !cached.isEmpty()) {
            log.info("[ContactServiceImpl] ✅ Returning {} matched contacts from Redis cache for userId={}",
                    cached.size(), ownerUserId);
            return cached.stream()
                    .filter(MatchedContactResponse.class::isInstance)
                    .map(MatchedContactResponse.class::cast)
                    .toList();
        }

        // ❌ Cache miss → fetch from DB
        List<MatchedContactResponse> fromDb = fetchFromDatabase(ownerUserId);
        if (!fromDb.isEmpty()) {
            fromDb.forEach(resp -> redisTemplate.opsForSet().add(key, resp));
            redisTemplate.expire(key, 30, TimeUnit.MINUTES);
            log.info("[ContactServiceImpl] 💾 Cached {} matched contacts from DB into Redis for userId={}",
                    fromDb.size(), ownerUserId);
        } else {
            log.info("[ContactServiceImpl] ⚠️ No matched contacts found for userId={}", ownerUserId);
        }

        return fromDb;
    }

    @Override
    public void evictMatchedCache(Long userId) {
        redisTemplate.delete(CACHE_PREFIX + userId);
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
        List<Contact> contacts = contactRepository.findByOwnerUserId(userId);

        if (contacts.isEmpty()) {
            log.info("[ContactServiceImpl] ⚠️ No contacts found for userId={}, cache not refreshed", userId);
            return;
        }

        List<MatchedContactResponse> responses = fetchFromDatabase(userId);

        String keyPrefix = "matched_contacts:" + userId + ":";
        int count = 0;
        for (MatchedContactResponse dto : responses) {
            redisTemplate.opsForValue().set(keyPrefix + dto.getContactId(), dto);
            count++;
        }

        log.info("[ContactServiceImpl] 🔄 Refreshed matched cache for userId={}, total={} contacts cached",
                userId, count);
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
                    Optional<User> userOpt = ContactUtil.matchPhoneToUser(phone.getValue(), allUsers);
                    boolean isRegistered = userOpt.isPresent();

                    if (isRegistered && matchedUser == null) {
                        matched = true;
                        matchedUser = userOpt.get();
                        matchedUserId = matchedUser.getId();
                        matchedCount++;
                        log.debug("[ContactServiceImpl] 📌 Phone {} matched to userId={}",
                                phone.getValue(), matchedUserId);
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
