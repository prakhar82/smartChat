/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================
 * ✅ PresenceService
 * ------------------------------------------------------------
 * Maintains real-time user presence state:
 * - Online/offline status
 * - Last seen timestamp (updated when user disconnects)
 * <p>
 * Thread-safe (ConcurrentHashMap) for WebSocket multi-thread use.
 * ============================================================
 */
@Slf4j
@Service
public class PresenceService {

    private static final String CLASS = "[PresenceService]";

    // 🧠 Track user online/offline state
    private final Map<String, Boolean> presenceMap = new ConcurrentHashMap<>();

    // 🕒 Track user last seen timestamps
    private final Map<String, Instant> lastSeenMap = new ConcurrentHashMap<>();

    // ============================================================
    // 🟢 Mark user online
    // ============================================================
    public void markOnline(String userId) {
        presenceMap.put(userId, true);
        lastSeenMap.put(userId, Instant.now()); // update for fresh timestamp
        log.info("{} 🟢 User {} marked ONLINE", CLASS, userId);
    }

    // ============================================================
    // 🔴 Mark user offline + record last seen
    // ============================================================
    public void markOffline(String userId) {
        presenceMap.put(userId, false);
        lastSeenMap.put(userId, Instant.now());
        log.info("{} 🔴 User {} marked OFFLINE (lastSeen={})", CLASS, userId, lastSeenMap.get(userId));
    }

    // ============================================================
    // 📡 Get presence snapshot
    // ============================================================
    public Map<String, Boolean> getAll() {
        return Map.copyOf(presenceMap);
    }

    // ============================================================
    // 🕒 Get last seen for specific user
    // ============================================================
    public Instant getLastSeen(String userId) {
        return lastSeenMap.get(userId);
    }

    // ============================================================
    // 🧾 Get full presence data: userId → { online, lastSeen }
    // ============================================================
    public Map<String, Map<String, Object>> getFullSnapshot() {
        Map<String, Map<String, Object>> snapshot = new ConcurrentHashMap<>();

        presenceMap.forEach((userId, online) -> {
            snapshot.put(userId, Map.of(
                    "online", online,
                    "lastSeen", lastSeenMap.getOrDefault(userId, Instant.EPOCH)
            ));
        });

        return snapshot;
    }
}
