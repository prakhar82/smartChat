/*
 * Copyright (c) 2025 SmartChat Contributors
 * Author: Prakhar Dwivedi
 *
 * Purpose:
 *   Manage contact sync from client and match against registered SmartChat users.
 *
 * Where to call:
 *   - Called by ContactController.syncContacts()
 */
package com.smartchat.backend.service;

import com.smartchat.backend.dto.MatchedContactResponse;

import java.util.List;

public interface ContactService {
    void syncContacts(com.smartchat.backend.dto.ContactSyncRequest request);
    List<MatchedContactResponse> getMatchedContacts(Long ownerUserId);
    public void evictMatchedCache(Long userId);
}
