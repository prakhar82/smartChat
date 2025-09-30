/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.smartchat.backend.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CONVO_KEY_PREFIX = "chat:messages:"; // conversation
    private static final String RECENT_KEY_PREFIX = "recent:chats:"; // per user

    private static final int MAX_RECENT = 50;   // Keep last 50 recent chats
    private static final int MAX_MESSAGES = 500; // Keep last 500 messages per conversation

    /**
     * Save message into cache for conversation + update recents.
     */
    public void pushMessageToCache(Long senderId, Long receiverId, ChatMessage msg) {
        String convoKey = buildConversationKey(senderId, receiverId);

        // Push into conversation list
        redisTemplate.opsForList().leftPush(convoKey, msg);
        redisTemplate.opsForList().trim(convoKey, 0, MAX_MESSAGES - 1);
        redisTemplate.expire(convoKey, 30, TimeUnit.DAYS);
        log.info("[ChatCacheService] 💬 Cached messageId={} into {}", msg.getId(), convoKey);

        // Push into recents for both users
        pushRecent(senderId, msg);
        pushRecent(receiverId, msg);
    }

    private void pushRecent(Long userId, ChatMessage msg) {
        String key = RECENT_KEY_PREFIX + userId;
        redisTemplate.opsForList().leftPush(key, msg);
        redisTemplate.opsForList().trim(key, 0, MAX_RECENT - 1);
        redisTemplate.expire(key, 30, TimeUnit.DAYS);
        log.info("[ChatCacheService] 📌 Updated recent chats for userId={} with messageId={}", userId, msg.getId());
    }

    /**
     * Update status of a cached message inside conversation list.
     */
    public void updateMessageStatusInCache(ChatMessage updated) {
        String convoKey = buildConversationKey(updated.getSenderId(), updated.getReceiverId());

        List<Object> cachedMessages = redisTemplate.opsForList().range(convoKey, 0, -1);
        if (cachedMessages == null || cachedMessages.isEmpty()) {
            log.warn("[ChatCacheService] ⚠️ No cached messages found in {} for messageId={}", convoKey, updated.getId());
            return;
        }

        for (int i = 0; i < cachedMessages.size(); i++) {
            Object obj = cachedMessages.get(i);
            if (obj instanceof ChatMessage cached && cached.getId().equals(updated.getId())) {
                redisTemplate.opsForList().set(convoKey, i, updated);
                log.info("[ChatCacheService] 🔄 Updated cached messageId={} with status={}", updated.getId(), updated.getStatus());
                return;
            }
        }

        log.warn("[ChatCacheService] ⚠️ MessageId={} not found in cache {}", updated.getId(), convoKey);
    }

    /**
     * Get conversation from cache (or DB fallback in service).
     */
    public List<Object> getConversationFromCache(Long u1, Long u2, int limit) {
        String convoKey = buildConversationKey(u1, u2);
        List<Object> list = redisTemplate.opsForList().range(convoKey, 0, limit - 1);
        log.debug("[ChatCacheService] 📥 Loaded {} messages from cache {}", list != null ? list.size() : 0, convoKey);
        return list;
    }

    /**
     * Build consistent conversation key (order-independent).
     */
    private String buildConversationKey(Long u1, Long u2) {
        return CONVO_KEY_PREFIX + (u1 < u2 ? u1 + ":" + u2 : u2 + ":" + u1);
    }

    /**
     * Evict all cache entries for a conversation or user.
     */
    public void evictConversation(Long u1, Long u2) {
        String key = buildConversationKey(u1, u2);
        redisTemplate.delete(key);
        log.info("[ChatCacheService] 🗑️ Evicted conversation cache {}", key);
    }

    public void evictRecent(Long userId) {
        String key = RECENT_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        log.info("[ChatCacheService] 🗑️ Evicted recent chats for userId={}", userId);
    }
}
