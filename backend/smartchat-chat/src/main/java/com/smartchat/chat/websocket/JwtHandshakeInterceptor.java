/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * ============================================================
 * JwtHandshakeInterceptor (Final)
 * ------------------------------------------------------------
 * ✔ SockJS-compatible: NEVER rejects handshake
 * ✔ Does NOT require token here
 * ✔ Only forwards query-token if present (optional)
 * ✔ Real authentication is done in JwtChannelInterceptor (STOMP CONNECT)
 * ============================================================
 */
@Slf4j
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String rawQuery = request.getURI().getQuery();
        String token = null;

        /* ---------------------------------------------------------------------
         * 1️⃣ Extract token from ?token=... (safe parsing)
         * ------------------------------------------------------------------- */
        if (rawQuery != null) {
            for (String part : rawQuery.split("&")) {
                if (part.startsWith("token=")) {
                    token = part.substring("token=".length());
                    log.debug("[JwtHandshake] Token found in query param");
                    break;
                }
            }
        }

        /* ---------------------------------------------------------------------
         * 2️⃣ Extract Authorization header if provided by native WebSocket clients
         * ------------------------------------------------------------------- */
        try {
            var headers = request.getHeaders();

            if (headers.containsKey("Authorization")) {
                String h = headers.getFirst("Authorization");
                if (h != null && h.startsWith("Bearer ")) {
                    token = h.substring(7);
                    log.debug("[JwtHandshake] Token extracted from Authorization header");
                }
            }
        } catch (Exception ignored) {
            log.warn("[JwtHandshake] ⚠️ Failed reading Authorization header");
        }

        /* ---------------------------------------------------------------------
         * 3️⃣ Store the token ONLY if present (STOMP CONNECT will authenticate)
         * ------------------------------------------------------------------- */
        if (token != null && !token.isBlank()) {
            attributes.put("token", token);
            log.info("[JwtHandshake] 🔍 Forwarding token into WebSocket session attributes");
        }

        /* ---------------------------------------------------------------------
         * IMPORTANT:
         * Always return TRUE → SockJS uses MANY handshake steps:
         *   /info, /xhr_streaming, /xhr_send, /websocket, /iframe.html, etc.
         * Rejecting any breaks connection.
         * ------------------------------------------------------------------- */
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}
