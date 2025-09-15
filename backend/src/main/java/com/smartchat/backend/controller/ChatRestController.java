/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * REST controller responsible for handling chat-related operations,
 * including fetching contacts, retrieving chat history, and sending messages.
 *
 * <p>Key features:
 * <ul>
 *   <li>Provides an endpoint to fetch all registered contacts (users).</li>
 *   <li>Retrieves chat history from Redis cache when available, falling back to database if not.</li>
 *   <li>Supports sending messages which are stored in both the database and Redis cache.</li>
 *   <li>Timestamps for messages are automatically managed at entity level via {@code @PrePersist}.</li>
 * </ul>
 *
 * <p>Base URL: {@code /api}</p>
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.dto.RecentChatResponse;
import com.smartchat.backend.model.ChatMessage;
import com.smartchat.backend.repository.ChatMessageRepository;
import com.smartchat.backend.service.ChatService;
import com.smartchat.backend.service.cache.ChatCacheServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatMessageRepository chatRepo;
    private final ChatCacheServiceImpl chatCache;
    private final ChatService chatService;

    @Value("${chat.history.limit:30}")
    private int historyLimit;

    @GetMapping("/recent")
    public List<RecentChatResponse> recentChats(@RequestParam Long userId) {
        return chatService.getRecentChats(userId);
    }

    @GetMapping("/{contactId}")
    public List<ChatMessage> getChatHistory(@PathVariable Long contactId,
                                            @RequestParam Long userId) {

        // 1. Try cache
        List<ChatMessage> cached = chatCache.get(userId, contactId);
        if (!cached.isEmpty()) {
            return cached;
        }

        // 2. Query DB (both directions)
        List<ChatMessage> dbMessages = chatRepo
                .findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByTimestampDesc(
                        userId, contactId,
                        contactId, userId,
                        PageRequest.of(0, historyLimit)
                );

        // 3. Cache merged messages
        chatCache.put(userId, contactId, dbMessages);

        return dbMessages;
    }

    @PostMapping("/send")
    public ChatMessage sendMessage(@RequestBody ChatMessage message) {
        return chatService.saveAndSend(message);
    }

    @PatchMapping("/status/{id}")
    public ChatMessage updateStatus(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        String status = body.get("status");
        return chatService.updateStatus(id, status);
    }
}
