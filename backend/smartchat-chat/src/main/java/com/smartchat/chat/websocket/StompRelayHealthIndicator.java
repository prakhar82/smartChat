/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.websocket;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

/**
 * ==========================================================
 * ✅ StompRelayHealthIndicator (Spring Boot 3.3+ Compatible)
 * ----------------------------------------------------------
 * Tracks broker availability (STOMP relay or simple broker).
 * Works with Spring 6.1 / Boot 3.3+ where the older
 * StompBrokerRelayAvailabilityEvent class was removed.
 * ==========================================================
 */
@Slf4j
@Component
public class StompRelayHealthIndicator {

    @Getter
    private volatile boolean relayAvailable = false;

    @EventListener
    public void handleBrokerAvailability(BrokerAvailabilityEvent event) {
        this.relayAvailable = event.isBrokerAvailable();
        log.info("[StompRelayHealthIndicator] 🔄 Broker availability changed → {}", relayAvailable);
    }
}
