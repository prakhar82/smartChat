/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.contracts.fallback;

import com.smartchat.common.contracts.ChatContract;
import com.smartchat.common.dto.MatchedContactResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class ChatContractFallback implements ChatContract {

    @Override
    public List<MatchedContactResponse> findMatchedContacts(String userId) {
        log.warn("[ChatContractFallback] ⚠️ Chat service unavailable. Returning empty matched contact list for userId={}", userId);
        return Collections.emptyList();
    }
}
