/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceMetricsService {

    private final MeterRegistry registry;

    private final AtomicInteger activeUsers = new AtomicInteger(0);
    private final AtomicInteger snapshotSize = new AtomicInteger(0);

    private Counter presenceUpdates;

    @PostConstruct
    void init() {
        Gauge.builder("smartchat_active_users_total", activeUsers, AtomicInteger::get)
                .description("Number of currently online SmartChat users")
                .register(registry);

        Gauge.builder("smartchat_presence_snapshot_size", snapshotSize, AtomicInteger::get)
                .description("Number of users in the latest presence snapshot")
                .register(registry);

        presenceUpdates = Counter.builder("smartchat_presence_updates_total")
                .description("Total number of presence updates processed")
                .register(registry);

        log.info("✅ Presence metrics initialized and registered with Micrometer");
    }

    public void updateActiveUsers(int count) {
        activeUsers.set(count);
    }

    public void updateSnapshotSize(int size) {
        snapshotSize.set(size);
    }

    public void incrementPresenceUpdate() {
        presenceUpdates.increment();
    }
}
