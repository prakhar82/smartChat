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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactServiceImpl implements ContactService {

    private final UserContactRepository userContactRepository;
    private final UserRepository userRepository;

    // ✅ Typed RedisTemplate for matched contacts
    private final RedisTemplate<String, List<MatchedContactResponse>> redisTemplate;

    private static final String MATCHED_CACHE_PREFIX = "matched_contacts:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    @Override
    public void syncContacts(ContactSyncRequest request) {
        if (request == null || request.getContacts() == null) return;

        List<UserContact> toSave = new ArrayList<>();

        for (var c : request.getContacts()) {
            userContactRepository.findByOwnerUserIdAndPhoneNormalized(
                    request.getOwnerUserId(),
                    c.getPhoneNormalized()
            ).ifPresentOrElse(existing -> {
                existing.setContactName(c.getContactName());
                existing.setUpdatedAt(Instant.now());
                toSave.add(existing);
            }, () -> {
                UserContact uc = new UserContact();
                uc.setOwnerUserId(request.getOwnerUserId());
                uc.setContactName(c.getContactName());
                uc.setPhoneNormalized(c.getPhoneNormalized());
                uc.setPhoneRaw(c.getPhoneRaw());
                uc.setSource("device");
                uc.setCreatedAt(Instant.now());
                uc.setUpdatedAt(Instant.now());
                toSave.add(uc);
            });
        }

        if (!toSave.isEmpty()) {
            userContactRepository.saveAll(toSave);
            evictMatchedCache(request.getOwnerUserId());
        }
    }

    @Override
    public List<MatchedContactResponse> getMatchedContacts(Long ownerUserId) {
        String cacheKey = MATCHED_CACHE_PREFIX + ownerUserId;

        // ✅ Check Redis first
        List<MatchedContactResponse> cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<UserContact> contacts = userContactRepository.findByOwnerUserId(ownerUserId);
        if (contacts.isEmpty()) return List.of();

        List<String> numbers = contacts.stream()
                .map(UserContact::getPhoneNormalized)
                .filter(Objects::nonNull)
                .toList();

        if (numbers.isEmpty()) return List.of();

        // ✅ Load all SmartChat users who match phone numbers
        List<User> matchedUsers = userRepository.findByMobileNormalizedIn(numbers);
        Set<String> matchedSet = matchedUsers.stream()
                .map(User::getMobileNormalized)
                .collect(Collectors.toSet());

        // ✅ Build response DTOs
        List<MatchedContactResponse> result = contacts.stream()
                .map(c -> new MatchedContactResponse(
                        c.getContactName() != null ? c.getContactName() : c.getPhoneNormalized(),
                        c.getId() != null ? c.getId().toString() : null, // 👈 directly use MongoDB ObjectId (String)
                        c.getPhoneNormalized(),
                        matchedSet.contains(c.getPhoneNormalized()),
                        c.getEmail()
                ))
                .toList();

        // ✅ Cache result with TTL
        redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL);

        return result;
    }


    @Override
    public void evictMatchedCache(Long userId) {
        try {
            redisTemplate.delete(MATCHED_CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Failed to evict matched contacts cache for user {}", userId, e);
        }
    }
}
