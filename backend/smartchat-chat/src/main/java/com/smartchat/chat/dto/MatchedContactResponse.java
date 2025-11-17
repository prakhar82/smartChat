/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO returned to frontend when querying matched contacts.
 * <p>
 * - supports multiple phones/emails per contact
 * - each phone has a 'registered' flag set by service logic
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchedContactResponse {

    private String contactId;
    private String contactName;

    /**
     * List of phone entries (label, value, registered)
     * e.g. label = "mobile" / "home" / "work"
     */
    private List<PhoneEntry> phones = new ArrayList<>();

    /**
     * List of email entries (label, value)
     */
    private List<EmailEntry> emails = new ArrayList<>();

    /**
     * True when any phone entry is registered on SmartChat
     */
    private boolean registered;

    /**
     * True if we can invite this contact (has email)
     */
    private boolean canInvite;

    /**
     * If matched to an existing user, this is that user's id (optional)
     */
    private Long matchedUserId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhoneEntry {
        private String label;
        private String value;
        private boolean registered;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailEntry {
        private String label;
        private String value;
    }
}
