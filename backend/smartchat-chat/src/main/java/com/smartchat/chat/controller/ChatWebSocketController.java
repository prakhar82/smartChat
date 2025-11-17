/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.controller;

import com.smartchat.chat.model.ChatMessage;
import com.smartchat.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;

/**
 * ==========================================================
 * 💬 ChatWebSocketController (Final Production Version)
 * ----------------------------------------------------------
 * Handles real-time chat messaging:
 * - /app/chat.send    => send chat message
 * - /app/chat.typing  => typing indicator
 * - /app/chat.ping    => heartbeat keepalive
 * <p>
 * Uses:
 * - Principal injected by WebSocketAuthInterceptor
 * - Per-user queues: /user/{id}/queue/**
 * ==========================================================
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    // ==========================================================
    // 1️⃣ SEND MESSAGE — /app/chat.send
    // ==========================================================
    @MessageMapping("/chat.send")
    public void processMessage(@Payload ChatMessage msg, Principal principal) {

        Long senderId = msg.getSenderId();
        Long receiverId = msg.getReceiverId();

        try {
            // 🔐 Validate sender identity
            if (principal != null && !principal.getName().equals(String.valueOf(senderId))) {
                log.warn("[ChatWS] ❌ Sender mismatch — principal={} attempted senderId={}",
                        principal.getName(), senderId);

                messagingTemplate.convertAndSendToUser(
                        principal.getName(),
                        "/queue/errors",
                        Map.of("error", "Unauthorized sender ID")
                );
                return;
            }

            // 💾 Save + fanout
            ChatMessage saved = chatService.saveAndSend(msg);

            // 📩 Deliver to receiver
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(receiverId),
                    "/queue/messages",
                    saved
            );

            // 📬 Notify sender (DELIVERED)
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(senderId),
                    "/queue/status",
                    Map.of("messageId", saved.getId(), "status", "DELIVERED")
            );

            log.info("[ChatWS] 💬 Message {} {} → {}",
                    saved.getId(), senderId, receiverId);

        } catch (Exception e) {
            log.error("[ChatWS] ❌ Error processing chat message: {}", e.getMessage(), e);

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(senderId),
                    "/queue/errors",
                    Map.of("error", "Failed to send message")
            );
        }
    }

    // ==========================================================
    // 2️⃣ TYPING INDICATOR — /app/chat.typing
    // ==========================================================
    @MessageMapping("/chat.typing")
    public void typing(@Payload Map<String, Object> body, Principal principal) {

        try {
            Long fromId = Long.parseLong(body.get("fromId").toString());
            Long toId = Long.parseLong(body.get("toId").toString());

            // 🔐 Ensure correct sender
            if (principal != null && !principal.getName().equals(String.valueOf(fromId))) {
                log.warn("[ChatWS] ⚠️ Unauthorized typing event from principal={} as fromId={}",
                        principal.getName(), fromId);
                return;
            }

            // 📡 Forward typing indicator to receiver
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(toId),
                    "/queue/typing",
                    Map.of("fromId", fromId, "toId", toId)
            );

            log.debug("[ChatWS] ✏️ Typing: {} → {}", fromId, toId);

        } catch (Exception e) {
            log.error("[ChatWS] ❌ typing error: {}", e.getMessage(), e);
        }
    }

    // ==========================================================
    // 3️⃣ HEARTBEAT — /app/chat.ping
    // ==========================================================
    @MessageMapping("/chat.ping")
    public void ping(@Payload Map<String, Object> payload, Principal principal) {

        try {
            if (principal == null) {
                log.warn("[ChatWS] ⚠️ Ping ignored — no principal");
                return;
            }

            String userId = principal.getName();
            log.trace("[ChatWS] 🫀 Ping from user={}", userId);

        } catch (Exception e) {
            log.error("[ChatWS] ❌ ping error: {}", e.getMessage(), e);
        }
    }

    // ==========================================================
    // 4️⃣ NOTIFY USER WHEN CONNECTED
    // ==========================================================
    @SubscribeMapping("/chat/connected")
    @SendToUser("/queue/status")
    public Map<String, Object> onConnect(Principal principal) {
        String id = principal != null ? principal.getName() : "unknown";

        log.info("[ChatWS] 🔗 User connected: {}", id);

        return Map.of(
                "status", "CONNECTED",
                "timestamp", Instant.now().toString()
        );
    }

    // ==========================================================
    // 5️⃣ ERROR HANDLER
    // ==========================================================
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, String> handleException(Exception ex) {
        log.error("[ChatWS] ⚠️ WebSocket exception: {}", ex.getMessage());
        return Map.of("error", ex.getMessage());
    }
}
