/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.controller;

import com.smartchat.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * PresenceWebSocketController
 * <p>
 * - Accepts presence-related STOMP messages from clients
 * - Sends full snapshots and accepts pings
 * - Always uses convertAndSendToUser(userId, "/queue/presence", payload)
 * instead of writing ad-hoc destinations that RabbitMQ considers invalid.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PresenceWebSocketController {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Client requests full presence snapshot (mapped from /app/presence/request)
     * We send the snapshot directly to the requesting user via /user/{id}/queue/presence.
     */
    @MessageMapping("/presence/request")
    public void requestSnapshot(Principal principal) {
        String targetUserId = null;
        try {
            if (principal != null) {
                targetUserId = principal.getName();
            }

            if (targetUserId == null || targetUserId.isBlank()) {
                log.warn("[PresenceController] ⚠️ Snapshot request without principal — ignoring");
                return;
            }

            // Build snapshot (could be heavy; keep concise value types)
            var snapshot = presenceService.getFullSnapshot();

            messagingTemplate.convertAndSendToUser(
                    targetUserId,
                    "/queue/presence",
                    Map.of(
                            "type", "SNAPSHOT",
                            "users", snapshot,
                            "timestamp", System.currentTimeMillis()
                    )
            );

            log.info("[PresenceController] 📡 Sent presence snapshot to user={}", targetUserId);

        } catch (Exception ex) {
            log.error("[PresenceController] ❌ Failed sending snapshot to user={} error={}", targetUserId, ex.getMessage(), ex);
        }
    }

    /**
     * Presence ping from client to refresh TTL. Mapped from /app/presence.ping
     * Body expected to contain userId or we fallback to Principal.
     */
    @MessageMapping("/presence.ping")
    public void presencePing(@Payload(required = false) Map<String, Object> body, Principal principal) {
        String userId = null;
        try {
            if (body != null && body.get("userId") != null) {
                userId = String.valueOf(body.get("userId"));
            } else if (principal != null) {
                userId = principal.getName();
            }

            if (userId == null || userId.isBlank()) {
                log.warn("[PresenceController] ⚠️ Presence ping without userId/principal — ignoring");
                return;
            }

            presenceService.refreshTTL(userId);

            // Optionally ack back to user (lightweight)
            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/presence",
                    Map.of("type", "PING_ACK", "timestamp", System.currentTimeMillis())
            );

            log.trace("[PresenceController] 🫀 Presence ping processed for user={}", userId);

        } catch (Exception ex) {
            log.error("[PresenceController] ❌ presencePing failed for user={} error={}", userId, ex.getMessage(), ex);
        }
    }
}
