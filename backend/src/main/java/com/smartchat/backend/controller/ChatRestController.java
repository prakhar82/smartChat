/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.model.ChatMessage;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.ChatMessageRepository;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.ChatCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatRestController {

    private final UserRepository userRepo;
    private final ChatMessageRepository chatRepo;
    private final ChatCacheService chatCache;

    @GetMapping("/contacts")
    public List<User> getContacts() {
        return userRepo.findAll();
    }

    @GetMapping("/chats/{contactId}")
    public List<ChatMessage> getChatHistory(@PathVariable Long contactId, @RequestParam Long userId) {
        List<ChatMessage> cached = chatCache.getLastMessages(userId, contactId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        List<ChatMessage> dbMessages =
                chatRepo.findTop30BySenderIdAndReceiverIdOrderByTimestampDesc(userId, contactId);

        dbMessages.forEach(chatCache::cacheMessage);
        return dbMessages;
    }

    @PostMapping("/chats/send")
    public ChatMessage sendMessage(@RequestBody ChatMessage msg) {
        msg.setTimestamp(LocalDateTime.now());
        ChatMessage saved = chatRepo.save(msg);
        chatCache.cacheMessage(saved);
        return saved;
    }
}
