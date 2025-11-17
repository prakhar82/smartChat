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
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * JwtChannelInterceptor (Final)
 * - Validates JWT strictly at STOMP CONNECT
 * - Works with SockJS (no auth in handshake)
 * - Token from Authorization, token:, or handshake attrs
 * - Correct header extraction with safe casting
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtFeignClient jwtFeignClient;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        log.info("[JwtWS] 🔍 Processing STOMP CONNECT, session={}", accessor.getSessionId());

        String token = null;

        /* 1️⃣ Authorization: Bearer <token> */
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            if (header.startsWith("Bearer ")) {
                token = header.substring(7);
            }
        }

        /* 2️⃣ token: <token> header */
        if (token == null) {
            List<String> tokenHeaders = accessor.getNativeHeader("token");
            if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
                token = tokenHeaders.get(0);
            }
        }

        /* 3️⃣ Handshake stored token (JwtHandshakeInterceptor) */
        if (token == null && accessor.getSessionAttributes() != null) {
            Object handshakeToken = accessor.getSessionAttributes().get("token");
            if (handshakeToken != null) {
                token = handshakeToken.toString();
            }
        }

        /* 4️⃣ SockJS XHR fallback — native headers from CONNECT
         * EXACT FIX — safe cast to Map<String, List<Object>>
         */
        if (token == null) {

            Object rawNativeHeaders =
                    accessor.getHeader(SimpMessageHeaderAccessor.NATIVE_HEADERS);

            if (rawNativeHeaders instanceof Map<?, ?> rawMap) {

                @SuppressWarnings("unchecked")
                Map<String, List<Object>> nativeHeaders =
                        (Map<String, List<Object>>) rawMap;

                List<Object> list = nativeHeaders.get("Authorization");

                if (list != null && !list.isEmpty()) {
                    Object val = list.get(0);
                    if (val instanceof String s && s.startsWith("Bearer ")) {
                        token = s.substring(7);
                        log.debug("[JwtWS] Fallback extracted token from native headers");
                    }
                }

                if (token == null) {
                    List<Object> tokenList = nativeHeaders.get("token");
                    if (tokenList != null && !tokenList.isEmpty()) {
                        Object val = tokenList.get(0);
                        if (val != null) {
                            token = val.toString();
                            log.debug("[JwtWS] Fallback extracted token from native 'token' header");
                        }
                    }
                }
            }
        }

        /* Reject if no token */
        if (token == null || token.isBlank()) {
            log.warn("[JwtWS] ❌ Rejecting CONNECT — no JWT provided");
            return null;
        }

        try {
            UserDTO user = jwtFeignClient.validateToken(token).getBody();

            if (user == null) {
                log.warn("[JwtWS] ❌ JWT invalid — rejecting CONNECT");
                return null;
            }

            /* Principal */
            Principal principal = () -> String.valueOf(user.getId());
            accessor.setUser(principal);

            /* Store session attributes for PresenceEventListener fallback */
            accessor.getSessionAttributes().put("userId", user.getId());
            accessor.getSessionAttributes().put("username", user.getPhoneNumber());
            accessor.getSessionAttributes().put("token", token);

            log.info(
                    "[JwtWS] 🟢 CONNECT authenticated: user={} id={} session={}",
                    user.getPhoneNumber(),
                    user.getId(),
                    accessor.getSessionId()
            );

        } catch (Exception ex) {
            log.error("[JwtWS] ❌ Token validation failed: {}", ex.getMessage());
            return null;
        }

        return message;
    }
}
