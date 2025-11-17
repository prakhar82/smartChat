/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.dto;

import java.time.LocalDateTime;

/**
 * DTO for WhatsApp-style recent chat list
 */
public record RecentChatResponse(
        String contactId,
        String contactName,
        String phoneNormalized,
        boolean registered,
        String lastMessage,
        LocalDateTime lastMessageTime
) {
}
