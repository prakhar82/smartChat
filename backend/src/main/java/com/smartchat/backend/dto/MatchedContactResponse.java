/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.dto;

import lombok.Data;

/**
 * Returned to the client: only matched SmartChat users from a user's contacts.
 */
@Data
public class MatchedContactResponse {
    private Long userId;
    private String phoneNumber;
    private String name;
}
