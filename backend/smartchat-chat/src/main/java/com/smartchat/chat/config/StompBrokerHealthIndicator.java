/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ==========================================================
 * 🩺 StompBrokerHealthIndicator
 * ----------------------------------------------------------
 * Monitors availability of the RabbitMQ STOMP relay broker.
 * - Listens to {@link BrokerAvailabilityEvent}
 * - Integrates with Spring Boot Actuator Health endpoint
 * - Used internally for frontend readiness check
 * ==========================================================
 */
@Slf4j
@Component
public class StompBrokerHealthIndicator implements HealthIndicator {

    private final AtomicBoolean brokerAvailable = new AtomicBoolean(false);

    @EventListener
    public void onBrokerAvailability(BrokerAvailabilityEvent event) {
        boolean available = event.isBrokerAvailable();
        brokerAvailable.set(available);
        log.info("[StompBrokerHealthIndicator] 🩺 STOMP relay broker is now {}", available ? "AVAILABLE ✅" : "UNAVAILABLE ❌");
    }

    @Override
    public Health health() {
        if (brokerAvailable.get()) {
            return Health.up()
                    .withDetail("stompBroker", "Relay available")
                    .withDetail("status", true)
                    .build();
        } else {
            return Health.down()
                    .withDetail("stompBroker", "Relay not available")
                    .withDetail("status", false)
                    .build();
        }
    }

    /**
     * Utility for programmatic access (used by readiness controller).
     */
    public boolean isBrokerAvailable() {
        return brokerAvailable.get();
    }
}
