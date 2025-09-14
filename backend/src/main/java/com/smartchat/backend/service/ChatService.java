/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.smartchat.backend.model.ChatMessage;
import com.smartchat.backend.repository.ChatMessageRepository;
import com.smartchat.backend.service.cache.ChatCacheServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatRepo;
    private final ChatCacheServiceImpl chatCache;

    @Transactional
    public ChatMessage saveAndSend(ChatMessage msg) {
        // 1. Save to DB
        ChatMessage saved = chatRepo.save(msg);

        // 2. Evict cache for both directions
        chatCache.evict(saved.getSenderId(), saved.getReceiverId());

        return saved;
    }

    /**
     * Update the status of a message (e.g., SENT, DELIVERED, READ).
     */
    @Transactional
    public ChatMessage updateStatus(Long messageId, String status) {
        ChatMessage message = chatRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        message.setStatus(status); // Assuming ChatMessage has a `status` field
        ChatMessage updated = chatRepo.save(message);

        // Optional: Update cache with the new status
        chatCache.updateMessageStatusInCache(updated);

        return updated;
    }
}
