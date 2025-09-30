/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class StompEventsListener {

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        System.out.println("✅ STOMP session connected: " + event.getMessage());
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        System.out.println("⚠️ STOMP session disconnected: " + event.getCloseStatus());
    }

    @EventListener
    public void handleBrokerAvailability(BrokerAvailabilityEvent event) {
        if (event.isBrokerAvailable()) {
            System.out.println("🟢 STOMP broker relay is available");
        } else {
            System.out.println("🔴 STOMP broker relay is DOWN");
        }
    }
}
