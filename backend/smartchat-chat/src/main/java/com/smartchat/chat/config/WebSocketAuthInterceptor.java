/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smartchat.common.contracts.JwtFeignClient;
import com.smartchat.common.dto.UserDTO;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * ==========================================================
 * 🔐 WebSocketAuthInterceptor
 * ----------------------------------------------------------
 * Performs JWT-based authentication for STOMP WebSocket sessions.
 * <p>
 * ✅ Validates tokens through the Auth microservice (via Feign client)
 * ✅ Extracts username, userId, and mobile from the verified token
 * ✅ Sets authenticated user into the WebSocket session context
 * ✅ Handles and logs all authentication or connection errors clearly
 * ==========================================================
 * <p>
 * ⚙️ Change Log (Nov 2025)
 * ----------------------------------------------------------
 * 🟩 Issue Fixed:
 * Feign `JwtFeignClient.validateToken()` now returns `UserDTO`
 * instead of `Boolean`, matching the Auth microservice response.
 * <p>
 * Previously:  ResponseEntity<Boolean>
 * Now:         ResponseEntity<UserDTO>
 * <p>
 * 🧠 Why:
 * - The Auth service’s `/auth/token` endpoint returns full user info
 * (UserDTO) upon validation, not just a Boolean.
 * - This change prevents JSON deserialization errors like:
 * ❌ "Cannot deserialize value of type java.lang.Boolean from Object value"
 * <p>
 * ✅ Behavior:
 * - The interceptor now interprets a non-null `UserDTO` as a valid token.
 * - Username, userId, and mobile are populated from the DTO.
 * - No existing functionality or flow has been removed.
 * ==========================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtFeignClient jwtFeignClient;

    // In-memory JWT cache to minimize Feign load for short-term reuse
    private final Cache<String, Boolean> jwtCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(5000)
            .build();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        // Only process authentication on initial CONNECT
        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String sessionId = Optional.ofNullable(accessor.getSessionId()).orElse("unknown");
        String token = extractToken(accessor);

        if (token == null || token.isBlank()) {
            log.warn("[WebSocketAuthInterceptor] ⚠️ No JWT provided for session={}", sessionId);
            accessor.getSessionAttributes().put("authError", "MISSING_TOKEN");
            return message;
        }

        try {
            // ==========================================================
            // 1️⃣ Validate token via Auth Service (Feign)
            // ==========================================================
            boolean valid = false;
            UserDTO user = null;

            if (Boolean.TRUE.equals(jwtCache.getIfPresent(token))) {
                valid = true;
                log.trace("[WebSocketAuthInterceptor] ✅ Cached JWT validation success");
            } else {
                log.trace("[WebSocketAuthInterceptor] 🔍 Calling jwtFeignClient.validateToken(token)");
                user = jwtFeignClient.validateToken(token).getBody();
                valid = (user != null);

                if (valid) {
                    jwtCache.put(token, true);
                }
            }

            if (!valid) {
                log.warn("[WebSocketAuthInterceptor] 🔒 Invalid or expired JWT (session={})", sessionId);
                accessor.getSessionAttributes().put("authError", "INVALID_TOKEN");
                return message;
            }

            // ==========================================================
            // 2️⃣ Extract user claims (from validated UserDTO or via Feign)
            // ==========================================================
            String username = (user != null) ? user.getPhoneNumber() : jwtFeignClient.extractUsername(token).getBody();
            Long userId = (user != null) ? Long.valueOf(user.getId()) : jwtFeignClient.extractUserId(token).getBody();
            String mobile = (user != null) ? user.getPhoneNumber() : jwtFeignClient.extractMobile(token).getBody();

            if (username == null || username.isBlank()) {
                log.warn("[WebSocketAuthInterceptor] ⚠️ Missing username claim (session={})", sessionId);
                accessor.getSessionAttributes().put("authError", "MISSING_CLAIMS");
                return message;
            }

            // ==========================================================
            // 3️⃣ Create authenticated WebSocket principal
            // ==========================================================
            List<SimpleGrantedAuthority> authorities =
                    Collections.singletonList(new SimpleGrantedAuthority("USER"));

            var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
            accessor.setUser(authentication);

            log.info("[WebSocketAuthInterceptor] ✅ Authenticated WebSocket user='{}' (id={}, mobile={}, session={})",
                    username, userId, mobile, sessionId);

        } catch (Exception ex) {
            log.error("[WebSocketAuthInterceptor] ❌ Error validating JWT (session={}): {}", sessionId, ex.getMessage(), ex);
            accessor.getSessionAttributes().put("authError", "AUTH_ERROR");
        }

        return message;
    }

    // ==========================================================
    // 🧩 Helper: Extract JWT from Header or SockJS Query Param
    // ==========================================================
    private String extractToken(StompHeaderAccessor accessor) {
        // 1️⃣ From Authorization header
        String authHeader = Optional.ofNullable(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)).orElse("");
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2️⃣ From token header / SockJS param
        String tokenParam = accessor.getFirstNativeHeader("token");
        if (tokenParam != null && !tokenParam.isBlank()) {
            log.trace("[WebSocketAuthInterceptor] 🧩 Extracted token from query/header param");
            return tokenParam;
        }

        return null;
    }
}
