/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.smartchat.backend.dto.MatchedContactResponse;
import com.smartchat.backend.util.ContactUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisContactCacheService {
    private final RedisTemplate<String, Object> redisTemplate;

    public void cacheRawContacts(Long userId, String json, int ttlMinutes) {
        String key = ContactUtil.cacheKey(userId, "raw");
        redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttlMinutes));
    }

    public void cacheMatchedContacts(Long userId, List<MatchedContactResponse> contacts) {
        String key = ContactUtil.cacheKey(userId, "matched");
        redisTemplate.delete(key);
        for (MatchedContactResponse c : contacts) {
            redisTemplate.opsForSet().add(key, c);
        }
        redisTemplate.expire(key, Duration.ofMinutes(30));
    }

    public Set<Object> getMatchedContacts(Long userId) {
        String key = ContactUtil.cacheKey(userId, "matched");
        return redisTemplate.opsForSet().members(key);
    }

    public void evictMatchedCache(Long userId) {
        redisTemplate.delete(ContactUtil.cacheKey(userId, "matched"));
    }
}
