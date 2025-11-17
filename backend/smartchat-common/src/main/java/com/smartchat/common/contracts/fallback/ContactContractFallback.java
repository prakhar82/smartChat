/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.contracts.fallback;

import com.smartchat.common.contracts.ContactContract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ContactContractFallback implements ContactContract {

    @Override
    public void fetchAndSync(Long ownerUserId, String accessToken) {
        log.warn("[ContactContractFallback] ⚠️ Contact service unavailable. Skipping sync for userId={}", ownerUserId);
    }
}
