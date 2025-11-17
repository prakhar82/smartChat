/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.websocket;// FULL CLASS — FIXED — NO CASTING ERRORS
// (Paste and use directly)

import com.smartchat.chat.config.RabbitConfig;
import com.smartchat.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RabbitConfig rabbitConfig;

    @EventListener
    public void onConnect(SessionConnectEvent event) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();

        String userId = null;
        boolean usedFallback = false;

        // 1️⃣ Principal (most reliable)
        if (principal != null) {
            userId = principal.getName();
            log.debug("[PresenceListener] CONNECT principal={}", userId);
        }

        // 2️⃣ Session attributes fallback (from JwtChannelInterceptor)
        if (userId == null) {
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs != null && attrs.get("userId") != null) {
                userId = String.valueOf(attrs.get("userId"));
                usedFallback = true;
                log.debug("[PresenceListener] CONNECT fallback (sessionAttrs) userId={}", userId);
            }
        }

        // 3️⃣ Native headers fallback — safe casting fixed
        if (userId == null) {

            Object rawNativeHeaders =
                    accessor.getHeader(SimpMessageHeaderAccessor.NATIVE_HEADERS);

            if (rawNativeHeaders instanceof Map<?, ?> rawMap) {

                @SuppressWarnings("unchecked")
                Map<String, List<Object>> nativeHeaders =
                        (Map<String, List<Object>>) rawMap;

                List<Object> list = nativeHeaders.get("userId");

                if (list != null && !list.isEmpty()) {
                    Object val = list.get(0);
                    if (val != null) {
                        userId = String.valueOf(val);
                        usedFallback = true;
                        log.debug("[PresenceListener] CONNECT fallback (nativeHeaders) userId={}", userId);
                    }
                }
            }
        }

        if (userId == null || userId.isBlank()) {
            log.warn("[PresenceListener] ⚠️ SessionConnectEvent without principal — skipping");
            return;
        }

        if (!userId.matches("\\d+")) {
            log.warn("[PresenceListener] ⚠️ Invalid userId format: {}", userId);
            return;
        }

        try {
            rabbitConfig.declareUserQueues(userId);
        } catch (Exception e) {
            log.warn("[PresenceListener] ⚠️ Failed declaring queues for {}: {}", userId, e.getMessage());
        }

        try {
            presenceService.markOnline(userId);
        } catch (Exception e) {
            log.warn("[PresenceListener] ⚠️ markOnline failed for {}: {}", userId, e.getMessage());
        }

        try {
            messagingTemplate.convertAndSend("/topic/presence", Map.of(
                    "userId", userId,
                    "online", true,
                    "lastSeen", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.warn("[PresenceListener] ⚠️ Failed broadcasting connect for {}: {}", userId, e.getMessage());
        }

        log.info("[PresenceListener] 🟢 User {} connected{}", userId,
                usedFallback ? " (fallback)" : "");
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();

        String userId = null;
        boolean usedFallback = false;

        if (principal != null) {
            userId = principal.getName();
            log.debug("[PresenceListener] DISCONNECT principal={}", userId);
        }

        if (userId == null) {
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs != null && attrs.get("userId") != null) {
                userId = String.valueOf(attrs.get("userId"));
                usedFallback = true;
                log.debug("[PresenceListener] DISCONNECT fallback (sessionAttrs) userId={}", userId);
            }
        }

        if (userId == null || userId.isBlank()) {
            log.warn("[PresenceListener] ⚠️ SessionDisconnectEvent without principal — skipping");
            return;
        }

        try {
            presenceService.markOffline(userId);
        } catch (Exception e) {
            log.warn("[PresenceListener] ⚠️ markOffline failed for {}: {}", userId, e.getMessage());
        }

        try {
            messagingTemplate.convertAndSend("/topic/presence", Map.of(
                    "userId", userId,
                    "online", false,
                    "lastSeen", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.warn("[PresenceListener] ⚠️ Failed broadcasting disconnect for {}: {}", userId, e.getMessage());
        }

        log.info("[PresenceListener] 🔴 User {} disconnected{}", userId,
                usedFallback ? " (fallback)" : "");
    }
}
