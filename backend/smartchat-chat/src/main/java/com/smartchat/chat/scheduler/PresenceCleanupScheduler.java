/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.scheduler;

import com.smartchat.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ============================================================
 * 🕒 PresenceCleanupScheduler
 * ------------------------------------------------------------
 * Distributed cleanup process for SmartChat presence tracking.
 * <p>
 * ✅ Detects expired Redis presence keys (TTL auto-expiry)
 * ✅ Broadcasts 🔴 OFFLINE to all clients via /topic/presence
 * ✅ Updates metrics + memory state in PresenceService
 * <p>
 * Works across multiple chat nodes — Redis is the single
 * source of truth for online state.
 * ============================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceCleanupScheduler {

    private static final String CLASS = "[PresenceCleanupScheduler]";
    private static final String PREFIX = "presence:user:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    // ============================================================
    // 🕒 Scheduled cleanup — every 15 seconds
    // ============================================================
    @Scheduled(fixedRate = 15_000)
    public void cleanupExpiredUsers() {
        try {
            // 1️⃣ Fetch online users from Redis (source of truth)
            Set<String> onlineNow = presenceService.getAllOnlineFromRedis();

            // 2️⃣ Get previously known users (local memory)
            Map<String, Boolean> lastKnown = presenceService.getAll();

            // 3️⃣ Detect users who were online but are now gone
            Set<String> nowOffline = new HashSet<>();
            for (Map.Entry<String, Boolean> entry : lastKnown.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue()) && !onlineNow.contains(entry.getKey())) {
                    nowOffline.add(entry.getKey());
                }
            }

            if (nowOffline.isEmpty()) {
                log.trace("{} ✅ No expired users detected", CLASS);
                return;
            }

            // 4️⃣ Mark each expired user as offline + broadcast
            for (String userId : nowOffline) {
                log.info("{} 🔴 Redis TTL expired — marking user {} OFFLINE", CLASS, userId);
                presenceService.markOffline(userId);

                messagingTemplate.convertAndSend("/topic/presence", Map.of(
                        "userId", userId,
                        "online", false,
                        "timestamp", System.currentTimeMillis()
                ));
            }

            log.debug("{} 🧹 Cleanup done: {} users marked offline", CLASS, nowOffline.size());

        } catch (Exception e) {
            log.error("{} ❌ Error during cleanup: {}", CLASS, e.getMessage(), e);
        }
    }
}
