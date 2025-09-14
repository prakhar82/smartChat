/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * Sent from client to upload a list of phone numbers to the backend for matching.
 *
 * Called from mobile/web after reading device + Google contacts.
 */
@Data
public class ContactSyncRequest {
    private Long userId;
    private List<Contect> contacts;
    private Long ownerUserId;
}
