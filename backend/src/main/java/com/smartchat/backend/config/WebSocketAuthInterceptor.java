/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.config;

import com.smartchat.backend.auth.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        // Only handle CONNECT frames (initial WebSocket handshake)
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    // ✅ Extract claims using your JwtUtil
                    Claims claims = jwtUtil.extractAllClaims(token);
                    String username = claims.getSubject();
                    Long userId = claims.get("userId", Long.class);

                    if (username != null && userId != null) {
                        var auth = new UsernamePasswordAuthenticationToken(
                                userId,  // principal → userId
                                null,
                                null
                        );
                        accessor.setUser(auth);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        log.info("[WebSocketAuthInterceptor] ✅ Authenticated user={} (id={})", username, userId);
                    }
                } catch (Exception e) {
                    log.warn("[WebSocketAuthInterceptor] ❌ Invalid WebSocket token: {}", e.getMessage());
                }
            } else {
                log.warn("[WebSocketAuthInterceptor] ⚠️ Missing Authorization header in WebSocket CONNECT");
            }
        }

        return message;
    }
}
