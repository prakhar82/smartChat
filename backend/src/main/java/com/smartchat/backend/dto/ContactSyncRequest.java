/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.dto;

import com.smartchat.backend.model.Contact;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Sent from client to upload a list of contacts to the backend for matching.
 * <p>
 * Called from mobile/web after reading device + Google contacts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactSyncRequest {

    private Long userId;  // the requesting user
    private List<Contect> contacts; // 👈 matches frontend payload
    private Long ownerUserId; // optional override

    /**
     * Convert incoming request contacts to DB entities
     */
    public List<Contact> toEntities(Long ownerUserId) {
        return contacts.stream()
                .map(c -> {
                    Contact contact = new Contact();
                    contact.setOwnerUserId(ownerUserId);
                    contact.setContactName(c.getContactName());
                    contact.setPhones(c.getPhones());
                    contact.setEmails(c.getEmails());
                    return contact;
                })
                .toList();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Contect {
        private String contactName;
        private List<PhoneEntry> phones;
        private List<EmailEntry> emails;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhoneEntry {
        private String label;     // "mobile", "work", "home"
        private String value;     // phone number
        private boolean registered;


    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailEntry {
        private String label;     // "personal", "work"
        private String value;     // email address
    }
}
