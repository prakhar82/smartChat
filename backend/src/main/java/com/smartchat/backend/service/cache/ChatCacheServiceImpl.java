/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service.cache;

import com.smartchat.backend.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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

    private static final String CHAT_CACHE_PREFIX = "chat:conv:";

    private String conversationKey(Long userA, Long userB) {
        long x = Math.min(userA, userB);
        long y = Math.max(userA, userB);
        return CHAT_CACHE_PREFIX + x + ":" + y;
    }

    @Override
    public void pushMessageToCache(Long userId, Long contactId, ChatMessage message) {
        String key = conversationKey(userId, contactId);
        ListOperations<String, ChatMessage> ops = redisTemplate.opsForList();
        ops.leftPush(key, message);
        ops.trim(key, 0, historyLimit - 1);
        redisTemplate.expire(key, ttlMinutes, TimeUnit.MINUTES);

        log.info("[ChatCacheServiceImpl] ✅ Cached messageId={} for conversation {}<->{} (limit={}, ttl={}m)",
                message.getId(), userId, contactId, historyLimit, ttlMinutes);
    }

    @Override
    public void updateMessageStatusInCache(ChatMessage message) {
        if (message == null || message.getId() == null) return;

        String key = conversationKey(message.getSenderId(), message.getReceiverId());
        ListOperations<String, ChatMessage> ops = redisTemplate.opsForList();
        Long size = ops.size(key);

        if (size == null || size == 0) {
            log.debug("[ChatCacheServiceImpl] ⚠ No cache found for key={} ({}<->{}), skipping status update",
                    key, message.getSenderId(), message.getReceiverId());
            return;
        }

        for (int i = 0; i < size; i++) {
            ChatMessage cached = ops.index(key, i);
            if (cached != null && Objects.equals(cached.getId(), message.getId())) {
                ops.set(key, i, message);
                log.info("[ChatCacheServiceImpl] 🔄 Updated cached messageId={} with status={} at key={}",
                        message.getId(), message.getStatus(), key);
                return;
            }
        }

        log.debug("[ChatCacheServiceImpl] ⚠ MessageId={} not found in cache key={}", message.getId(), key);
    }


    @Override
    public void removeMessageFromCache(ChatMessage message) {
        if (message == null || message.getId() == null) return;
        String key = conversationKey(message.getSenderId(), message.getReceiverId());
        ListOperations<String, ChatMessage> ops = redisTemplate.opsForList();
        Long size = ops.size(key);
        if (size == null || size == 0) return;

        for (int i = 0; i < size; i++) {
            ChatMessage cached = ops.index(key, i);
            if (cached != null && Objects.equals(cached.getId(), message.getId())) {
                ops.set(key, i, message);
                log.debug("[ChatCacheServiceImpl] 🗑 Soft-deleted message in cache: messageId={} at index={}", message.getId(), i);
                return;
            }
        }
    }


    @Override
    public List<ChatMessage> get(Long userA, Long userB) {
        String key = conversationKey(userA, userB);
        List<ChatMessage> messages = redisTemplate.opsForList().range(key, 0, -1);
        log.info("[ChatCacheServiceImpl] 📥 Fetched {} cached messages for conversation {}<->{}",
                messages != null ? messages.size() : 0, userA, userB);
        return messages;
    }

    @Override
    public void evict(Long userA, Long userB) {
        String key = conversationKey(userA, userB);
        redisTemplate.delete(key);
        log.info("[ChatCacheServiceImpl] 🗑️ Evicted cache for conversation {}<->{}", userA, userB);
    }

    @Override
    public void put(Long userA, Long userB, List<ChatMessage> messages) {
        String key = conversationKey(userA, userB);
        ListOperations<String, ChatMessage> ops = redisTemplate.opsForList();
        redisTemplate.delete(key);

        if (messages != null && !messages.isEmpty()) {
            ops.rightPushAll(key, messages);
            ops.trim(key, 0, historyLimit - 1);
            log.info("[ChatCacheServiceImpl] 💾 Stored {} messages in cache for conversation {}<->{} (limit={})",
                    messages.size(), userA, userB, historyLimit);
        } else {
            log.debug("[ChatCacheServiceImpl] ⚠ No messages provided for cache put {}<->{}", userA, userB);
        }

        redisTemplate.expire(key, ttlMinutes, TimeUnit.MINUTES);
    }
}
