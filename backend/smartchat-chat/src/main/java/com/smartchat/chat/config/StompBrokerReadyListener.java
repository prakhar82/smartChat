/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

/**
 * Monitors when the STOMP broker relay becomes ready after startup.
 * Allows the frontend to wait until the relay is connected to RabbitMQ.
 */
@Component
public class StompBrokerReadyListener {

    private static volatile boolean brokerAvailable = false;

    @EventListener
    public void onBrokerAvailabilityChange(BrokerAvailabilityEvent event) {
        brokerAvailable = event.isBrokerAvailable();
        System.out.println("[StompBrokerReadyListener] 🚀 Broker availability = " + brokerAvailable);
    }

    public static boolean isBrokerAvailable() {
        return brokerAvailable;
    }
}