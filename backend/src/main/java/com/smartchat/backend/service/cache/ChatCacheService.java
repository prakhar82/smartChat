/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service.cache;

import com.smartchat.backend.model.ChatMessage;

import java.util.List;

public interface ChatCacheService {
    List<ChatMessage> get(Long userA, Long userB);

    void put(Long userA, Long userB, List<ChatMessage> messages);

    void pushMessageToCache(Long userA, Long userB, ChatMessage message);

    void updateMessageStatusInCache(ChatMessage message);

    void removeMessageFromCache(ChatMessage message);

    void evict(Long userA, Long userB);
}
