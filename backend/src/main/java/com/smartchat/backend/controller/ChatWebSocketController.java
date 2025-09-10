/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.model.ChatMessage;
import com.smartchat.backend.repository.ChatMessageRepository;
import com.smartchat.backend.service.ChatCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatRepo;
    private final ChatCacheService chatCache;

    @MessageMapping("/chat.send")
    public void processMessage(ChatMessage message) {
        message.setTimestamp(LocalDateTime.now());
        ChatMessage saved = chatRepo.save(message);
        chatCache.cacheMessage(saved);

        messagingTemplate.convertAndSendToUser(
                message.getReceiverId().toString(),
                "/topic/messages",
                saved
        );
    }
}

