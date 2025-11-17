/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * [MatchedContactResponse]
 * ------------------------------------------------------------
 * Shared DTO between contact, chat, and common modules.
 * Represents a contact entry that may or may not match
 * a registered SmartChat user.
 * ------------------------------------------------------------
 * ✅ Used for Redis caching & contact sync responses
 * ✅ Designed to remain decoupled from entity classes
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MatchedContactResponse {

    private String contactId;
    private String contactName;

    // Nested phone/email entries (like device contacts)
    private List<PhoneEntry> phones;
    private List<EmailEntry> emails;

    // Whether this contact matches a SmartChat-registered user
    private boolean matched;
    private boolean canInvite;

    // If matched, the actual SmartChat userId of that contact
    private Long matchedUserId;

    // Optional — last message snippet or status
    private String lastMessage;

    // Online presence flag (for UI)
    private boolean isOnline;

    // ==========================================================
    // 🔹 Inner DTOs for phone/email info
    // ==========================================================
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PhoneEntry {
        private String label;
        private String value;
        private boolean registered;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class EmailEntry {
        private String label;
        private String value;
    }
}
