/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartchat.backend.dto.ContactSyncRequest;
import com.smartchat.backend.dto.MatchedContactResponse;
import com.smartchat.backend.model.User;
import com.smartchat.backend.model.UserContact;
import com.smartchat.backend.repository.UserContactRepository;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.ContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactServiceImpl implements ContactService {

    private final UserContactRepository userContactRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, List<MatchedContactResponse>> redisTemplate;

    private static final String MATCHED_CACHE_PREFIX = "matched_contacts:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void syncContacts(ContactSyncRequest request) {
        if (request == null || request.getContacts() == null) return;

        List<UserContact> toSave = new ArrayList<>();

        for (var c : request.getContacts()) {
            if (c.getPhoneNormalized() == null && c.getPhoneRaw() == null) {
                continue;
            }

            Optional<UserContact> existingOpt = Optional.empty();
            if (c.getPhoneNormalized() != null) {
                existingOpt = userContactRepository.findByOwnerUserIdAndPhoneNormalized(
                        request.getOwnerUserId(), c.getPhoneNormalized());
            } else if (c.getPhoneRaw() != null) {
                existingOpt = userContactRepository.findByOwnerUserIdAndPhoneRaw(
                        request.getOwnerUserId(), c.getPhoneRaw());
            }

            if (existingOpt.isPresent()) {
                UserContact existing = existingOpt.get();
                existing.setContactName(c.getContactName());
                existing.setUpdatedAt(Instant.now());
                toSave.add(existing);
            } else {
                UserContact uc = new UserContact();
                uc.setOwnerUserId(request.getOwnerUserId());
                uc.setContactName(c.getContactName());
                uc.setPhoneNormalized(c.getPhoneNormalized());
                uc.setPhoneRaw(c.getPhoneRaw());
                uc.setSource("device");
                uc.setCreatedAt(Instant.now());
                uc.setUpdatedAt(Instant.now());
                toSave.add(uc);
            }
        }

        if (!toSave.isEmpty()) {
            userContactRepository.saveAll(toSave);
            evictMatchedCache(request.getOwnerUserId());
        }
    }

    @Override
    public List<MatchedContactResponse> getMatchedContacts(Long ownerUserId) {
        String cacheKey = MATCHED_CACHE_PREFIX + ownerUserId;

        List<MatchedContactResponse> cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return refreshRegisteredState(cached);
        }

        List<UserContact> contacts = userContactRepository.findByOwnerUserId(ownerUserId);
        if (contacts.isEmpty()) return List.of();

        List<MatchedContactResponse> result = buildMatchedList(contacts);

        redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL);
        return result;
    }

    private List<MatchedContactResponse> refreshRegisteredState(List<MatchedContactResponse> contacts) {
        if (contacts == null || contacts.isEmpty()) return contacts;

        List<String> numbers = contacts.stream()
                .flatMap(c -> c.getPhones().stream().map(MatchedContactResponse.PhoneEntry::getValue))
                .filter(Objects::nonNull)
                .toList();

        Set<String> matchedSet;
        if (!numbers.isEmpty()) {
            matchedSet = userRepository.findByMobileNormalizedIn(numbers)
                    .stream()
                    .map(User::getMobileNormalized)
                    .collect(Collectors.toSet());
        } else {
            matchedSet = Collections.emptySet();
        }

        return contacts.stream().peek(c -> {
            boolean hasRegistered = false;
            for (MatchedContactResponse.PhoneEntry p : c.getPhones()) {
                boolean registered = matchedSet.contains(p.getValue());
                p.setRegistered(registered);
                if (registered) hasRegistered = true;
            }
            c.setRegistered(hasRegistered);
        }).toList();
    }

    private List<MatchedContactResponse> buildMatchedList(List<UserContact> contacts) {
        List<String> numbers = contacts.stream()
                .map(UserContact::getPhoneNormalized)
                .filter(Objects::nonNull)
                .toList();

        Set<String> matchedSet = numbers.isEmpty()
                ? Collections.emptySet()
                : userRepository.findByMobileNormalizedIn(numbers)
                .stream()
                .map(User::getMobileNormalized)
                .collect(Collectors.toSet());

        Map<String, MatchedContactResponse> grouped = new LinkedHashMap<>();

        for (UserContact c : contacts) {
            String key = (c.getContactName() != null ? c.getContactName()
                    : (c.getPhoneNormalized() != null ? c.getPhoneNormalized()
                    : (c.getEmail() != null ? c.getEmail() : UUID.randomUUID().toString())));

            MatchedContactResponse existing = grouped.computeIfAbsent(key, k -> {
                MatchedContactResponse res = new MatchedContactResponse();
                res.setContactId(c.getId() != null ? c.getId().toString() : UUID.randomUUID().toString());
                res.setContactName(c.getContactName() != null ? c.getContactName() : "Unknown");
                res.setPhones(new ArrayList<>());
                res.setEmails(new ArrayList<>());
                res.setRegistered(false);
                res.setCanInvite(false);
                return res;
            });

            if (c.getPhoneNormalized() != null) {
                boolean isRegistered = matchedSet.contains(c.getPhoneNormalized());
                existing.getPhones().add(new MatchedContactResponse.PhoneEntry(
                        c.getLabel() != null ? c.getLabel() : "mobile",
                        c.getPhoneNormalized(),
                        isRegistered
                ));
                if (isRegistered) existing.setRegistered(true);
            }

            if (c.getEmail() != null && !c.getEmail().isBlank()) {
                existing.getEmails().add(new MatchedContactResponse.EmailEntry(
                        c.getLabel() != null ? c.getLabel() : "home",
                        c.getEmail()
                ));
                existing.setCanInvite(true);
            }
        }

        return new ArrayList<>(grouped.values());
    }

    @Override
    public void evictMatchedCache(Long userId) {
        try {
            redisTemplate.delete(MATCHED_CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Failed to evict matched contacts cache for user {}", userId, e);
        }
    }

    @Override
    public boolean userHasContacts(Long userId) {
        return userContactRepository.existsByOwnerUserId(userId);
    }
}
