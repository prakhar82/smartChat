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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PresenceService {

    private final Map<String, Boolean> presenceMap = new ConcurrentHashMap<>();

    public void markOnline(String userId) {
        presenceMap.put(userId, true);
        log.info("[PresenceService] 🟢 User {} marked ONLINE", userId);
    }

    public void markOffline(String userId) {
        presenceMap.put(userId, false);
        log.info("[PresenceService] 🔴 User {} marked OFFLINE", userId);
    }

    public Map<String, Boolean> getAll() {
        return presenceMap;
    }

    public boolean isOnline(String userId) {
        return presenceMap.getOrDefault(userId, false);
    }
}
