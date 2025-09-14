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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ChatCacheServiceImpl implements ChatCacheService {

    @Value("${chat.cache.ttl-minutes:60}")
    private long ttlMinutes;

    @Value("${chat.history.limit:30}")
    private int historyLimit;

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
    }

    @Override
    public void updateMessageStatusInCache(ChatMessage message) {
        if (message == null || message.getId() == null) return;

        String key = conversationKey(message.getSenderId(), message.getReceiverId());
        ListOperations<String, ChatMessage> ops = redisTemplate.opsForList();
        Long size = ops.size(key);

        if (size == null || size == 0) return;

        for (int i = 0; i < size; i++) {
            ChatMessage cached = ops.index(key, i);
            if (cached != null && Objects.equals(cached.getId(), message.getId())) {
                ops.set(key, i, message);
                return;
            }
        }
    }

    @Override
    public List<ChatMessage> get(Long userA, Long userB) {
        String key = conversationKey(userA, userB);
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    @Override
    public void evict(Long userA, Long userB) {
        redisTemplate.delete(conversationKey(userA, userB));
    }

    @Override
    public void put(Long userId, Long contactId, List<ChatMessage> messages) {
        String key = conversationKey(userId, contactId);
        ListOperations<String, ChatMessage> ops = redisTemplate.opsForList();

        redisTemplate.delete(key);

        if (messages != null && !messages.isEmpty()) {
            ops.rightPushAll(key, messages);
            ops.trim(key, 0, historyLimit - 1);
        }

        redisTemplate.expire(key, ttlMinutes, TimeUnit.MINUTES);
    }
}
