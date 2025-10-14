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
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * ============================================================
 * ✅ PresenceWebSocketController
 * ------------------------------------------------------------
 * Handles presence-related STOMP messages:
 * - /app/presence/update → mark user online/offline
 * - /app/presence/request → send full snapshot
 * <p>
 * Uses authenticated userId injected from JwtChannelInterceptor.
 * Broadcasts updates to:
 * - /topic/presence (incremental changes)
 * - /topic/presence/snapshot (full state)
 * ============================================================
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PresenceWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    /**
     * ============================================================
     * 🟢 Handle Online/Offline Updates
     * ------------------------------------------------------------
     * Called when client publishes to `/app/presence/update`
     * Example payload: { "online": true }
     * ============================================================
     */
    @MessageMapping("/presence/update")
    public void updatePresence(Map<String, Object> payload,
                               Principal principal,
                               SimpMessageHeaderAccessor headers) {

        Long userId = extractUserIdFromSession(principal, headers);
        if (userId == null) {
            log.warn("[PresenceController] ⚠️ Skipping presence update — userId missing or unauthenticated");
            return;
        }

        boolean online = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("online", true)));

        if (online) {
            presenceService.markOnline(String.valueOf(userId));
        } else {
            presenceService.markOffline(String.valueOf(userId));
        }

        log.info("[PresenceController] 👤 User {} is now {}", userId, online ? "🟢 ONLINE" : "🔴 OFFLINE");

        // ✅ Broadcast incremental update to all clients
        messagingTemplate.convertAndSend("/topic/presence", Map.of(
                "userId", userId,
                "online", online
        ));
    }

    /**
     * ============================================================
     * 📡 Handle Snapshot Requests
     * ------------------------------------------------------------
     * Called when client publishes to `/app/presence/request`
     * ============================================================
     */
    @MessageMapping("/presence/request")
    public void sendSnapshot(Principal principal, SimpMessageHeaderAccessor headers) {
        Long userId = extractUserIdFromSession(principal, headers);
        if (userId == null) {
            log.warn("[PresenceController] ⚠️ Snapshot request skipped — unauthenticated session");
            return;
        }

        Map<String, Map<String, Object>> snapshot = presenceService.getFullSnapshot();
        log.info("[PresenceController] 📡 Sending presence snapshot ({} users) to user={}", snapshot.size(), userId);

        messagingTemplate.convertAndSend("/topic/presence/snapshot", snapshot);
    }


    /**
     * ============================================================
     * 🧩 Helper — Extract Authenticated userId
     * ------------------------------------------------------------
     * Works with JwtChannelInterceptor → session attributes or principal
     * ============================================================
     */
    private Long extractUserIdFromSession(Principal principal, SimpMessageHeaderAccessor headers) {
        try {
            // 1️⃣ From session attributes (set in JwtChannelInterceptor)
            Object attr = headers.getSessionAttributes() != null
                    ? headers.getSessionAttributes().get("userId")
                    : null;
            if (attr != null) return Long.parseLong(attr.toString());

            // 2️⃣ Fallback: Principal name or object
            if (principal != null) {
                try {
                    return Long.parseLong(principal.getName());
                } catch (NumberFormatException e) {
                    log.debug("[PresenceController] Principal not numeric: {}", principal.getName());
                }
            }

            log.warn("[PresenceController] ⚠️ Unable to extract userId from WebSocket session");
            return null;
        } catch (Exception e) {
            log.error("[PresenceController] ❌ Failed to extract userId: {}", e.getMessage());
            return null;
        }
    }
}
