/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.smartchat.backend.dto.ContactSyncRequest;
import com.smartchat.backend.dto.MatchedContactResponse;

import java.util.List;

public interface ContactService {
    void syncContacts(ContactSyncRequest request);

    List<MatchedContactResponse> getMatchedContacts(Long ownerUserId);

    void evictMatchedCache(Long userId);

    boolean userHasContacts(Long userId);

    // NEW: Force reload of cache
    void refreshMatchedCache(Long userId);
}
