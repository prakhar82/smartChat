/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.smartchat.backend.dto.RecentChatResponse;
import com.smartchat.backend.model.ChatMessage;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.ChatMessageRepository;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.cache.ChatCacheServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatRepo;
    private final ChatCacheServiceImpl chatCache;
    private final UserRepository userRepo;

    /**
     * Get WhatsApp-style recent chat list
     */
    public List<RecentChatResponse> getRecentChats(Long userId) {
        List<ChatMessage> latest = chatRepo.findLatestMessagesByUser(userId);

        return latest.stream().map(msg -> {
            Long contactId = msg.getSenderId().equals(userId) ? msg.getReceiverId() : msg.getSenderId();
            User contact = userRepo.findById(contactId).orElse(null);

            return new RecentChatResponse(
                    String.valueOf(contactId),
                    contact != null ? contact.getFirstName() : "Unknown",
                    contact != null ? contact.getMobileNormalized() : null,
                    contact != null,
                    msg.getMessage(),
                    msg.getTimestamp()
            );
        }).toList();
    }

    @Transactional
    public ChatMessage saveAndSend(ChatMessage msg) {
        ChatMessage saved = chatRepo.save(msg);
        chatCache.evict(saved.getSenderId(), saved.getReceiverId());
        return saved;
    }

    @Transactional
    public ChatMessage updateStatus(Long messageId, String status) {
        ChatMessage message = chatRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        message.setStatus(status);
        ChatMessage updated = chatRepo.save(message);

        chatCache.updateMessageStatusInCache(updated);
        return updated;
    }
}
