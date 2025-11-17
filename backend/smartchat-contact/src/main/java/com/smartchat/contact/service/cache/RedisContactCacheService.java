/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.service.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartchat.common.dto.MatchedContactResponse;
import com.smartchat.contact.util.ContactUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * [RedisContactCacheService]
 * ------------------------------------------------------------
 * Caches user contacts and matched contact data in Redis.
 * ✅ Supports raw + matched contact caching
 * ✅ Stores data as compact JSON arrays
 * ✅ Handles TTL & safe eviction
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisContactCacheService {

    private static final String CLASS = "[RedisContactCacheService]";
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==========================================================
    // 🧠 Cache Raw Contacts (from Google / Device)
    // ==========================================================
    public void cacheRawContacts(Long userId, String json, int ttlMinutes) {
        String key = ContactUtil.cacheKey(userId, "raw");
        try {
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttlMinutes));
            log.info("{} 💾 Cached raw contacts for userId={} (TTL={}m)", CLASS, userId, ttlMinutes);
        } catch (Exception e) {
            log.error("{} ❌ Failed to cache raw contacts for userId={} → {}", CLASS, userId, e.getMessage(), e);
        }
    }

    // ==========================================================
    // 💬 Cache Matched Contacts
    // ==========================================================
    public void cacheMatchedContacts(Long userId, List<MatchedContactResponse> contacts) {
        String key = ContactUtil.cacheKey(userId, "matched");
        try {
            String json = objectMapper.writeValueAsString(contacts);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(30));
            log.info("{} 💾 Cached {} matched contacts for userId={} (TTL=30m)", CLASS, contacts.size(), userId);
        } catch (Exception e) {
            log.error("{} ❌ Failed to cache matched contacts for userId={} → {}", CLASS, userId, e.getMessage(), e);
        }
    }

    // ==========================================================
    // 🧾 Retrieve Matched Contacts
    // ==========================================================
    public List<MatchedContactResponse> getMatchedContacts(Long userId) {
        String key = ContactUtil.cacheKey(userId, "matched");
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                log.debug("{} ⚠️ Cache miss for matched contacts userId={}", CLASS, userId);
                return Collections.emptyList();
            }

            List<MatchedContactResponse> list = objectMapper.readValue(
                    json, new TypeReference<>() {
                    }
            );

            log.debug("{} ⚡ Cache hit: {} matched contacts found for userId={}", CLASS, list.size(), userId);
            return list;
        } catch (Exception e) {
            log.error("{} ❌ Failed to read matched contacts for userId={} → {}", CLASS, userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ==========================================================
    // 🗑️ Evict Cache
    // ==========================================================
    public void evictMatchedCache(Long userId) {
        String key = ContactUtil.cacheKey(userId, "matched");
        try {
            redisTemplate.delete(key);
            log.info("{} 🗑️ Evicted matched contact cache for userId={}", CLASS, userId);
        } catch (Exception e) {
            log.error("{} ❌ Failed to evict cache for userId={} → {}", CLASS, userId, e.getMessage(), e);
        }
    }
}
