/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */
package com.smartchat.backend.service;

import com.smartchat.backend.dto.MatchedContactResponse;

import java.util.List;

public interface ContactService {
    void syncContacts(com.smartchat.backend.dto.ContactSyncRequest request);

    List<MatchedContactResponse> getMatchedContacts(Long ownerUserId);

    public void evictMatchedCache(Long userId);

    public boolean userHasContacts(Long userId);

}
