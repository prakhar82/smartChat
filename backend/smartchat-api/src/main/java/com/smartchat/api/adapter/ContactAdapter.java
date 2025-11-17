/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.api.adapter;

import com.smartchat.common.contracts.ContactContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ContactAdapter
 * ----------------------------------------------------
 * Provides safe and clean way to trigger contact syncs
 * without direct coupling to Feign.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactAdapter {

    private final ContactContract contactContract;

    public void syncContacts(Long userId, String token) {
        try {
            contactContract.fetchAndSync(userId, token);
            log.info("[ContactAdapter] ✅ Sync triggered for userId={}", userId);
        } catch (Exception e) {
            log.error("[ContactAdapter] ⚠️ Failed to sync contacts: {}", e.getMessage());
        }
    }
}
