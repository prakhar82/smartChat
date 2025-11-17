/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.contracts.fallback;

import com.smartchat.common.contracts.ChatHealthContract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatHealthContractFallback implements ChatHealthContract {

    @Override
    public String getRelayHealth() {
        log.warn("[ChatHealthContractFallback] ⚠️ Chat service unavailable — returning DOWN");
        return "DOWN";
    }
}
