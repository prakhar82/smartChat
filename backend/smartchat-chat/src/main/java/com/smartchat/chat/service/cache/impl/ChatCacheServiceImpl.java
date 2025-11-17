/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.service.cache.impl;

import com.smartchat.chat.model.ChatMessage;
import com.smartchat.chat.service.cache.ChatCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * ==========================================================
 * 💾 ChatCacheServiceImpl
 * ----------------------------------------------------------
 * Redis-based conversation cache using LIST operations.
 * <p>
 * 🔹 Key Format:
 * chat:conv:<smallUserId>:<largeUserId>
 * <p>
 * 🔹 Features:
 * - Keeps latest N messages (historyLimit)
 * - Auto-expiry (ttlMinutes)
 * - Fast LINDEX/SET updates for read receipts
 * - Soft delete operations
 * ==========================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCacheServiceImpl implements ChatCacheService {

    @Value("${chat.cache.ttl-minutes:60}")
    private long ttlMinutes;

    @Value("${chat.history.limit:30}")
    private int historyLimit;

    @Qualifier("chatMessageRedisTemplate")
    private final RedisTemplate<String, ChatMessage> redisTemplate;

    private static final String PREFIX = "chat:conv:";

    // ==========================================================
    // 🔑 Utility — canonical conversation key
    // ==========================================================
    private String key(Long userA, Long userB) {
        long x = Math.min(userA, userB);
        long y = Math.max(userA, userB);
        return PREFIX + x + ":" + y;
    }

    // ==========================================================
    // ➕ PUSH NEW MESSAGE TO CACHE
    // ==========================================================
    @Override
    public void pushMessageToCache(Long userId, Long contactId, ChatMessage message) {
        String redisKey = key(userId, contactId);

        try {
            ListOperations<String, ChatMessage> ops = redisTemplate.opsForList();

            ops.leftPush(redisKey, message);
            ops.trim(redisKey, 0, historyLimit - 1);

            redisTemplate.expire(redisKey, ttlMinutes, TimeUnit.MINUTES);

            log.debug("[ChatCacheService] 💾 Pushed message {} into cache {} (limit={}, ttl={}m)",
                    message.getId(), redisKey, historyLimit, ttlMinutes);

        } catch (DataAccessException e) {
            log.error("[ChatCacheService] ❌ pushMessageToCache failed for {} → {}", redisKey, e.getMessage());
        }
    }

    // ==========================================================
    // ✏️ UPDATE STATUS IN CACHE
    // ==========================================================
    @Override
    public void updateMessageStatusInCache(ChatMessage message) {
        if (message == null || message.getId() == null) return;

        String redisKey = key(message.getSenderId(), message.getReceiverId());

        try {
            ListOperations<String, ChatMessage> ops = redisTemplate.opsForList();
            Long size = ops.size(redisKey);

            if (size == null || size == 0) return;

            for (int i = 0; i < size; i++) {
                ChatMessage cached = ops.index(redisKey, i);
                if (cached != null && Objects.equals(cached.getId(), message.getId())) {
                    ops.set(redisKey, i, message);
                    redisTemplate.expire(redisKey, ttlMinutes, TimeUnit.MINUTES);

                    log.debug("[ChatCacheService] 🔄 Updated messageId={} → status={} in {}",
                            message.getId(), message.getStatus(), redisKey);
                    return;
                }
            }

        } catch (DataAccessException e) {
            log.error("[ChatCacheService] ❌ updateMessageStatusInCache failed for {} → {}", redisKey, e.getMessage());
        }
    }

    // ==========================================================
    // 🗑 SOFT DELETE FROM CACHE
    // ==========================================================
    @Override
    public void removeMessageFromCache(ChatMessage message) {
        if (message == null || message.getId() == null) return;

        String redisKey = key(message.getSenderId(), message.getReceiverId());

        try {
            ListOperations<String, ChatMessage> ops = redisTemplate.opsForList();
            Long size = ops.size(redisKey);

            if (size == null || size == 0) return;

            for (int i = 0; i < size; i++) {
                ChatMessage cached = ops.index(redisKey, i);
                if (cached != null && Objects.equals(cached.getId(), message.getId())) {

                    ops.set(redisKey, i, message);
                    redisTemplate.expire(redisKey, ttlMinutes, TimeUnit.MINUTES);

                    log.debug("[ChatCacheService] 🗑 Soft-deleted messageId={} at index={} in {}",
                            message.getId(), i, redisKey);
                    return;
                }
            }

        } catch (DataAccessException e) {
            log.error("[ChatCacheService] ❌ removeMessageFromCache failed for {} → {}", redisKey, e.getMessage());
        }
    }

    // ==========================================================
    // 📥 GET MESSAGES FROM CACHE
    // ==========================================================
    @Override
    public List<ChatMessage> get(Long userA, Long userB) {
        String redisKey = key(userA, userB);

        try {
            List<ChatMessage> list =
                    redisTemplate.opsForList().range(redisKey, 0, -1);

            int count = list != null ? list.size() : 0;

            log.trace("[ChatCacheService] 📥 Fetched {} cached messages for {}", count, redisKey);

            return list != null ? list : Collections.emptyList();

        } catch (DataAccessException e) {
            log.error("[ChatCacheService] ❌ get failed for {} → {}", redisKey, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==========================================================
    // 💾 PUT FULL CONVERSATION INTO CACHE
    // ==========================================================
    @Override
    public void put(Long userA, Long userB, List<ChatMessage> messages) {
        String redisKey = key(userA, userB);

        try {
            ListOperations<String, ChatMessage> ops = redisTemplate.opsForList();

            redisTemplate.delete(redisKey);

            if (messages != null && !messages.isEmpty()) {
                ops.rightPushAll(redisKey, messages);
                ops.trim(redisKey, 0, historyLimit - 1);
            }

            redisTemplate.expire(redisKey, ttlMinutes, TimeUnit.MINUTES);

            log.debug("[ChatCacheService] 💾 Cached {} messages in {} (limit={})",
                    messages != null ? messages.size() : 0, redisKey, historyLimit);

        } catch (DataAccessException e) {
            log.error("[ChatCacheService] ❌ put failed for {} → {}", redisKey, e.getMessage());
        }
    }

    // ==========================================================
    // 🧹 EVICT CONVERSATION CACHE
    // ==========================================================
    @Override
    public void evict(Long userA, Long userB) {
        String redisKey = key(userA, userB);

        try {
            redisTemplate.delete(redisKey);

            log.debug("[ChatCacheService] 🧹 Evicted cache for {}", redisKey);

        } catch (DataAccessException e) {
            log.error("[ChatCacheService] ❌ evict failed for {} → {}", redisKey, e.getMessage());
        }
    }
}
