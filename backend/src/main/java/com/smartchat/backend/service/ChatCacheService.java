/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

package com.smartchat.backend.service;

import com.smartchat.backend.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ChatCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final int MAX_MESSAGES = 30;

    private String key(Long userId, Long contactId) {
        return "chat:" + userId + ":" + contactId;
    }

    public void cacheMessage(ChatMessage message) {
        String key = key(message.getSenderId(), message.getReceiverId());
        redisTemplate.opsForList().leftPush(key, message);
        redisTemplate.opsForList().trim(key, 0, MAX_MESSAGES - 1);
        redisTemplate.expire(key, 1, TimeUnit.DAYS); // optional
    }

    @SuppressWarnings("unchecked")
    public List<ChatMessage> getLastMessages(Long userId, Long contactId) {
        String key = key(userId, contactId);
        return (List<ChatMessage>) (List<?>) redisTemplate.opsForList().range(key, 0, -1);
    }

    public void clearChat(Long userId, Long contactId) {
        redisTemplate.delete(key(userId, contactId));
    }
}
