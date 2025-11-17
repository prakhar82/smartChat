/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.websocket;

import com.smartchat.common.contracts.JwtFeignClient;
import com.smartchat.common.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;

/**
 * ==========================================================
 * 🔐 AuthChannelInterceptor (Feign-based)
 * ----------------------------------------------------------
 * • Intercepts STOMP CONNECT frames.
 * • Extracts JWT from headers or SockJS query params.
 * • Validates token using the Auth microservice (via Feign).
 * • Builds an Authentication object and injects it into the STOMP session.
 * ==========================================================
 * <p>
 * ⚙️ Change Log (Nov 2025)
 * ----------------------------------------------------------
 * 🟩 Issue Fixed:
 * Feign `JwtFeignClient.validateToken()` now returns `UserDTO`
 * instead of `Boolean`, consistent with the Auth microservice.
 * <p>
 * 🧠 Why:
 * - The Auth `/auth/token` endpoint now returns complete user info,
 * not a simple Boolean flag.
 * - Prevents Feign decoding errors like:
 * ❌ "Cannot deserialize value of type java.lang.Boolean from Object value"
 * <p>
 * ✅ Behavior:
 * - Non-null `UserDTO` means a valid JWT.
 * - Username and userId are extracted from the DTO.
 * - Falls back to Feign claim extraction if needed.
 * - Still sets session attributes and authenticates via Spring Security.
 * ==========================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    private static final String CLASS = "[AuthChannelInterceptor]";

    private final JwtFeignClient jwtFeignClient;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Only process STOMP CONNECT frames
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        try {
            // ======================================================
            // 1️⃣ Extract Token
            // ======================================================
            String token = extractToken(accessor);
            if (token == null || token.isBlank()) {
                log.warn("{} ⚠️ Missing JWT in STOMP CONNECT → rejecting anonymously", CLASS);
                return message;
            }

            // ======================================================
            // 2️⃣ Validate Token via Auth Service
            // ======================================================
            UserDTO user = jwtFeignClient.validateToken(token).getBody();
            boolean valid = (user != null);

            if (!valid) {
                log.warn("{} ❌ Invalid or expired JWT token", CLASS);
                accessor.getSessionAttributes().put("authError", "INVALID_TOKEN");
                return message;
            }

            // ======================================================
            // 3️⃣ Extract username and userId (prefer from UserDTO)
            // ======================================================
            String username = user.getPhoneNumber();
            Long userId = Long.valueOf(user.getId());

            // Optional fallback via Feign if fields missing
            if (username == null || username.isBlank()) {
                username = jwtFeignClient.extractUsername(token).getBody();
            }
            if (userId == null) {
                userId = jwtFeignClient.extractUserId(token).getBody();
            }

            if (username == null || userId == null) {
                log.warn("{} ⚠️ Missing username or userId in token", CLASS);
                accessor.getSessionAttributes().put("authError", "MISSING_CLAIMS");
                return message;
            }

            // ======================================================
            // 4️⃣ Build Authentication Object
            // ======================================================
            var authorities = Collections.singletonList(new SimpleGrantedAuthority("USER"));
            Authentication authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
            accessor.setUser(authentication);

            // ======================================================
            // 5️⃣ Attach User Context to Session
            // ======================================================
            if (accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put("userId", userId);
                accessor.getSessionAttributes().put("username", username);
            }

            log.info("{} ✅ Authenticated STOMP user='{}' (id={})", CLASS, username, userId);

        } catch (Exception e) {
            log.error("{} ❌ Feign/JWT validation error → {}", CLASS, e.getMessage(), e);
            accessor.getSessionAttributes().put("authError", "AUTH_ERROR");
        }

        return message;
    }

    // ======================================================
    // 🧩 Helper: Extract JWT from headers or query param
    // ======================================================
    private String extractToken(StompHeaderAccessor accessor) {
        // 1️⃣ Standard "Authorization" header
        String authHeader = Optional.ofNullable(accessor.getFirstNativeHeader("Authorization")).orElse("");
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2️⃣ SockJS fallback (common for browsers)
        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            log.debug("{} 🧩 Extracted token from SockJS param", CLASS);
            return tokenHeader;
        }

        return null;
    }
}
