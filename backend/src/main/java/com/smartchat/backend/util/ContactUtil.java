/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.util;

import com.smartchat.backend.dto.ContactSyncRequest;
import com.smartchat.backend.dto.MatchedContactResponse;
import com.smartchat.backend.model.User;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class ContactUtil {

    /**
     * Normalize raw phone into E.164-like format (basic handling).
     * - Strips spaces, dashes, brackets
     * - Assumes Indian numbers by default if 10 digits
     * - Falls back to "+" + digits
     */
    public static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;

        // If 10 digits, assume it's India (+91)
        if (digits.length() == 10) {
            String normalized = "+91" + digits;
            log.debug("[ContactUtil] Normalized {} → {}", raw, normalized);
            return normalized;
        }

        // If starts with 91 and is 12 digits, assume already correct
        if (digits.startsWith("91") && digits.length() == 12) {
            String normalized = "+" + digits;
            log.debug("[ContactUtil] Normalized {} → {}", raw, normalized);
            return normalized;
        }

        // If starts with 0 and is 11 digits, strip the 0 and add +91
        if (digits.startsWith("0") && digits.length() == 11) {
            String normalized = "+91" + digits.substring(1);
            log.debug("[ContactUtil] Normalized {} → {}", raw, normalized);
            return normalized;
        }

        // Fallback
        String normalized = "+" + digits;
        log.debug("[ContactUtil] Normalized {} → {}", raw, normalized);
        return normalized;
    }

    /**
     * Try to match a raw phone number against users (country code + local number stored separately).
     */
    public static Optional<User> matchPhoneToUser(String rawPhone, List<User> users) {
        if (rawPhone == null || rawPhone.isBlank()) {
            log.debug("[ContactUtil] Skipping empty rawPhone");
            return Optional.empty();
        }

        // Remove spaces, dashes, parentheses
        String cleaned = rawPhone.replaceAll("[\\s\\-()]", "");
        log.debug("[ContactUtil] Checking phone={} (cleaned={}) against {} users", rawPhone, cleaned, users.size());

        // Case 1: starts with +
        if (cleaned.startsWith("+")) {
            for (User user : users) {
                String cc = user.getCountryCode();     // e.g. +91, +1, +972
                String local = user.getMobileNumber(); // e.g. 7888030330

                if (cc != null && local != null && cleaned.startsWith(cc)) {
                    String withoutCc = cleaned.substring(cc.length());
                    if (withoutCc.equals(local)) {
                        log.debug("[ContactUtil] ✅ Matched phone={} with userId={} via countryCode={}", cleaned, user.getId(), cc);
                        return Optional.of(user);
                    }
                }
            }
        }

        // Case 2: starts with 0 → strip leading zeros
        if (cleaned.startsWith("0")) {
            String stripped = cleaned.replaceFirst("^0+", "");
            for (User user : users) {
                if (user.getMobileNumber() != null &&
                        user.getMobileNumber().equals(stripped)) {
                    log.debug("[ContactUtil] ✅ Matched stripped phone={} with userId={}", stripped, user.getId());
                    return Optional.of(user);
                }
            }
        }

        // Case 3: direct compare with local number
        for (User user : users) {
            if (user.getMobileNumber() != null &&
                    user.getMobileNumber().equals(cleaned)) {
                log.debug("[ContactUtil] ✅ Direct matched phone={} with userId={}", cleaned, user.getId());
                return Optional.of(user);
            }
        }

        log.debug("[ContactUtil] ❌ No match found for phone={}", rawPhone);
        return Optional.empty();
    }

    /**
     * Utility to log a summary after processing a batch of contacts.
     */
    public static void logMatchSummary(int total, int matched, int unmatched) {
        log.info("[ContactUtil] Processed {} contacts → Matched: {}, Unmatched: {}", total, matched, unmatched);
    }

    /**
     * Helper that merges phone lists without duplicates (by value).
     */
    public static List<ContactSyncRequest.PhoneEntry> mergePhoneLists(
            List<ContactSyncRequest.PhoneEntry> existing,
            List<ContactSyncRequest.PhoneEntry> incoming) {

        Map<String, ContactSyncRequest.PhoneEntry> map = new LinkedHashMap<>();
        if (existing != null) {
            for (ContactSyncRequest.PhoneEntry p : existing) {
                map.put(p.getValue(), p);
            }
        }
        if (incoming != null) {
            for (ContactSyncRequest.PhoneEntry p : incoming) {
                map.putIfAbsent(p.getValue(), p);
            }
        }
        return new ArrayList<>(map.values());
    }

    /**
     * Helper that merges email lists without duplicates (case-insensitive).
     */
    public static List<ContactSyncRequest.EmailEntry> mergeEmailLists(
            List<ContactSyncRequest.EmailEntry> existing,
            List<ContactSyncRequest.EmailEntry> incoming) {

        Map<String, ContactSyncRequest.EmailEntry> map = new LinkedHashMap<>();
        if (existing != null) {
            for (ContactSyncRequest.EmailEntry e : existing) {
                map.put(e.getValue().toLowerCase(Locale.ROOT), e);
            }
        }
        if (incoming != null) {
            for (ContactSyncRequest.EmailEntry e : incoming) {
                map.putIfAbsent(e.getValue().toLowerCase(Locale.ROOT), e);
            }
        }
        return new ArrayList<>(map.values());
    }

    /**
     * 🔄 Helper: Sort contacts with registered users on top,
     * both groups alphabetically by contactName.
     */
    public static List<MatchedContactResponse> sortContacts(List<MatchedContactResponse> contacts) {
        return contacts.stream()
                .sorted((a, b) -> {
                    boolean aRegistered = a.getMatchedUserId() != null;
                    boolean bRegistered = b.getMatchedUserId() != null;

                    if (aRegistered && !bRegistered) return -1; // registered first
                    if (!aRegistered && bRegistered) return 1;

                    // alphabetical (null-safe)
                    String nameA = Optional.ofNullable(a.getContactName()).orElse("").toLowerCase();
                    String nameB = Optional.ofNullable(b.getContactName()).orElse("").toLowerCase();
                    return nameA.compareTo(nameB);
                })
                .toList();
    }

    /**
     * Generate cache key for user.
     */
    public static String cacheKey(Long userId) {
        return "contacts:matched:" + userId;
    }

    public static String cacheKey(Long userId, String type) {
        return "contacts:" + type + ":user:" + userId;
    }

}
