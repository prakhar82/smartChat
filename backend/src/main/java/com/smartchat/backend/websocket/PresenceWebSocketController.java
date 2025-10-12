/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
public class PresenceWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    public PresenceWebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/presence/update")
    public void updatePresence(Map<String, Object> payload) {
        String userId = String.valueOf(payload.get("userId"));
        boolean online = Boolean.parseBoolean(String.valueOf(payload.get("online")));

        log.info("[PresenceController] 👤 User {} is now {}", userId, online ? "Online" : "Offline");

        // Broadcast to all subscribers
        messagingTemplate.convertAndSend("/topic/presence", Map.of(
                "userId", userId,
                "online", online
        ));
    }
}
