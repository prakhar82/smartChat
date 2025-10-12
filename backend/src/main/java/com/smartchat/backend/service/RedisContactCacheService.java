/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartchat.backend.dto.MatchedContactResponse;
import com.smartchat.backend.util.ContactUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisContactCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void cacheRawContacts(Long userId, String json, int ttlMinutes) {
        String key = ContactUtil.cacheKey(userId, "raw");
        redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttlMinutes));
    }

    public void cacheMatchedContacts(Long userId, List<MatchedContactResponse> contacts) {
        String key = ContactUtil.cacheKey(userId, "matched");
        redisTemplate.delete(key);
        try {
            for (MatchedContactResponse c : contacts) {
                String json = objectMapper.writeValueAsString(c);
                redisTemplate.opsForSet().add(key, json);
            }
            redisTemplate.expire(key, Duration.ofMinutes(30));
            log.info("[RedisContactCacheService] 💾 Cached {} matched contacts for userId={}", contacts.size(), userId);
        } catch (Exception e) {
            log.error("[RedisContactCacheService] ❌ Failed to cache matched contacts for userId={} → {}", userId, e.getMessage());
        }
    }

    public List<MatchedContactResponse> getMatchedContacts(Long userId) {
        String key = ContactUtil.cacheKey(userId, "matched");
        Set<Object> raw = redisTemplate.opsForSet().members(key);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return raw.stream()
                    .filter(obj -> obj instanceof String)
                    .map(obj -> (String) obj)
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, MatchedContactResponse.class);
                        } catch (Exception e) {
                            log.error("[RedisContactCacheService] ❌ Failed to parse cached contact → {}", e.getMessage());
                            return null;
                        }
                    })
                    .filter(c -> c != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[RedisContactCacheService] ❌ Failed to read matched contacts for userId={} → {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public void evictMatchedCache(Long userId) {
        String key = ContactUtil.cacheKey(userId, "matched");
        redisTemplate.delete(key);
        log.info("[RedisContactCacheService] 🗑️ Evicted cache for {}", key);
    }
}
