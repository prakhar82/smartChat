/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.api.adapter;/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import com.smartchat.common.contracts.ChatHealthContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHealthAdapter {

    private final ChatHealthContract chatHealthContract;

    public boolean isRelayHealthy() {
        try {
            String status = chatHealthContract.getRelayHealth();
            log.info("[ChatHealthAdapter] 🔍 Feign returned raw: '{}'", status);

            boolean healthy = status != null && status.trim().equalsIgnoreCase("UP");
            log.info("[ChatHealthAdapter] ✅ Relay healthy = {}", healthy);
            return healthy;
        } catch (Exception e) {
            log.error("[ChatHealthAdapter] ❌ Error calling chat health: {}", e.getMessage(), e);
            return false;
        }
    }
}
