/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.websocket;

import com.smartchat.backend.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;

/**
 * ============================================================
 * ✅ PresenceEventListener
 * ------------------------------------------------------------
 * Tracks when users connect/disconnect via WebSocket.
 * Uses JwtChannelInterceptor-authenticated user info.
 * Broadcasts updates to `/topic/presence`.
 * ============================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * ============================================================
     * 🟢 When a WebSocket client connects
     * ============================================================
     */
    @EventListener
    public void onConnect(SessionConnectEvent event) {
        String userId = extractUserId(event.getUser(), event.getMessage().getHeaders());

        if (userId == null) {
            log.warn("[PresenceEventListener] ⚠️ Missing userId on connect — skipping");
            return;
        }

        presenceService.markOnline(userId);

        // 🔧 Broadcast immediately for near-instant UI reflection
        messagingTemplate.convertAndSend("/topic/presence", Map.of(
                "userId", userId,
                "online", true,
                "lastSeen", Instant.now()
        ));

        log.info("[PresenceEventListener] 🟢 User {} connected (broadcasted)", userId);
    }

    /**
     * ============================================================
     * 🔴 When a WebSocket client disconnects
     * ============================================================
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String userId = extractUserId(event.getUser(), event.getMessage().getHeaders());
        if (userId == null) {
            log.warn("[PresenceEventListener] ⚠️ Missing userId on disconnect — skipping");
            return;
        }

        presenceService.markOffline(userId);

        // 🔧 Broadcast offline event
        messagingTemplate.convertAndSend("/topic/presence", Map.of(
                "userId", userId,
                "online", false,
                "lastSeen", Instant.now()
        ));

        log.info("[PresenceEventListener] 🔴 User {} disconnected", userId);
    }

    /**
     * ============================================================
     * 🧩 Helper — Extract userId from Principal or session headers
     * ============================================================
     */
    private String extractUserId(Principal principal, Map<String, Object> headers) {
        // ✅ 1️⃣ From authenticated principal
        if (principal != null && principal.getName() != null) {
            return principal.getName();
        }

        // ✅ 2️⃣ Fallback: from STOMP session headers
        Object attrs = headers.get("simpSessionAttributes");
        if (attrs instanceof Map<?, ?> map) {
            Object uid = map.get("userId");
            if (uid != null) return String.valueOf(uid);
        }

        return null;
    }
}
