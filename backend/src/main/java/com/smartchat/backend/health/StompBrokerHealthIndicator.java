/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class StompBrokerHealthIndicator implements HealthIndicator {

    private final AtomicBoolean brokerAvailable = new AtomicBoolean(false);

    @EventListener
    public void onBrokerAvailability(BrokerAvailabilityEvent event) {
        brokerAvailable.set(event.isBrokerAvailable());
    }

    @Override
    public Health health() {
        if (brokerAvailable.get()) {
            return Health.up()
                    .withDetail("stompBroker", "Relay available")
                    .build();
        } else {
            return Health.down()
                    .withDetail("stompBroker", "Relay not available")
                    .build();
        }
    }
}
