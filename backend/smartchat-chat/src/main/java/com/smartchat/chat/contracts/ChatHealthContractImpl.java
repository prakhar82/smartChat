/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.contracts;

import com.smartchat.chat.websocket.StompRelayHealthIndicator;
import com.smartchat.common.contracts.ChatHealthContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Bridges chat module health information for internal use.
 * Implements the ChatHealthContract interface for self-registration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHealthContractImpl implements ChatHealthContract {

    private final StompRelayHealthIndicator stompRelayHealthIndicator;

    @Override
    public String getRelayHealth() {
        try {
            boolean up = stompRelayHealthIndicator.isRelayAvailable();
            log.debug("[ChatHealthContractImpl] STOMP relay health = {}", up ? "UP" : "DOWN");
            return up ? "UP" : "DOWN";
        } catch (Exception e) {
            log.error("[ChatHealthContractImpl] Failed to check STOMP relay health: {}", e.getMessage());
            return "DOWN";
        }
    }
}
