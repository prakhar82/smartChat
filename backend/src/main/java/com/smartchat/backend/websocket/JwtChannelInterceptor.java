/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.websocket;

import com.smartchat.backend.auth.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * ============================================================
 * ✅ JwtChannelInterceptor
 * ------------------------------------------------------------
 * Intercepts STOMP CONNECT frames and validates JWT tokens.
 * - Ensures only authenticated clients can establish WebSocket sessions
 * - Extracts userId from token for presence tracking
 * ============================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil; // ✅ using your existing JwtUtil class

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // Intercept only CONNECT commands
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String tokenHeader = accessor.getFirstNativeHeader("Authorization");

            if (tokenHeader == null || !tokenHeader.startsWith("Bearer ")) {
                log.warn("[JwtChannelInterceptor] ❌ Missing or invalid Authorization header in STOMP CONNECT");
                throw new IllegalArgumentException("Missing Authorization header");
            }

            String token = tokenHeader.substring(7);

            try {
                String username = jwtUtil.extractUsername(token);
                Long userId = jwtUtil.extractUserId(token);

                if (jwtUtil.isTokenExpired(token)) {
                    throw new IllegalArgumentException("Expired token");
                }

                log.info("[JwtChannelInterceptor] ✅ Authenticated WebSocket user → {} (userId={})", username, userId);

                // Store the userId in the session headers for later retrieval
                accessor.getSessionAttributes().put("userId", userId);

            } catch (Exception e) {
                log.error("[JwtChannelInterceptor] ❌ Invalid JWT → {}", e.getMessage());
                throw new IllegalArgumentException("Invalid WebSocket token: " + e.getMessage());
            }
        }

        return message;
    }
}
