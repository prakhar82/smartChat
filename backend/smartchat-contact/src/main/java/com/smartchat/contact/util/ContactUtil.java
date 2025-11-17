/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.util;

import com.smartchat.common.dto.MatchedContactResponse;
import com.smartchat.common.dto.UserDTO;
import com.smartchat.contact.dto.ContactSyncRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * [ContactUtil]
 * ------------------------------------------------------------
 * Common utilities for contact synchronization and normalization.
 * Used across Google sync, phonebook imports, and matching logic.
 * ------------------------------------------------------------
 * ✅ Handles E.164 normalization
 * ✅ Merges phones/emails safely
 * ✅ Provides summary logging and sorting
 */
@Slf4j
public class ContactUtil {

    private static final String CLASS = "[ContactUtil]";

    // ==========================================================
    // ☎️ Phone Normalization
    // ==========================================================

    /**
     * Normalize raw phone into E.164-like format (basic handling).
     * - Strips spaces, dashes, brackets
     * - Assumes Indian numbers by default if 10 digits
     * - Falls back to "+" + digits
     */
    public static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            log.debug("{} ⚠️ Skipping empty raw phone", CLASS);
            return null;
        }

        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;

        String normalized;
        if (digits.length() == 10) {
            normalized = "+91" + digits;
        } else if (digits.startsWith("91") && digits.length() == 12) {
            normalized = "+" + digits;
        } else if (digits.startsWith("0") && digits.length() == 11) {
            normalized = "+91" + digits.substring(1);
        } else {
            normalized = "+" + digits;
        }

        log.debug("{} ✅ Normalized {} → {}", CLASS, raw, normalized);
        return normalized;
    }

    // ==========================================================
    // 🔍 Match a normalized phone to known registered users
    // ==========================================================

    /**
     * Try to match a raw phone number against users (based on country code + local number).
     */
    public static Optional<UserDTO> matchPhoneToUser(String rawPhone, List<UserDTO> users) {
        if (rawPhone == null || rawPhone.isBlank()) {
            log.debug("{} ⚠️ Skipping empty phone number", CLASS);
            return Optional.empty();
        }

        String cleaned = rawPhone.replaceAll("[\\s\\-()]", "");
        log.debug("{} ▶ Checking phone={} (cleaned={}) against {} users", CLASS, rawPhone, cleaned, users.size());

        // Case 1: starts with + and matches full E.164
        if (cleaned.startsWith("+")) {
            for (UserDTO user : users) {
                if (user.getPhoneNumber() != null && cleaned.equals(user.getPhoneNumber())) {
                    log.debug("{} ✅ Matched full E.164 phone={} with userId={}", CLASS, cleaned, user.getId());
                    return Optional.of(user);
                }
            }
        }

        // Case 2: starts with 0 → strip leading zeros
        if (cleaned.startsWith("0")) {
            String stripped = cleaned.replaceFirst("^0+", "");
            for (UserDTO user : users) {
                String userNum = Optional.ofNullable(user.getPhoneNumber()).orElse("");
                if (userNum.endsWith(stripped)) {
                    log.debug("{} ✅ Matched stripped phone={} with userId={}", CLASS, stripped, user.getId());
                    return Optional.of(user);
                }
            }
        }

        // Case 3: direct compare (raw vs stored)
        for (UserDTO user : users) {
            String userNum = Optional.ofNullable(user.getPhoneNumber()).orElse("");
            if (userNum.equals(cleaned)) {
                log.debug("{} ✅ Direct matched phone={} with userId={}", CLASS, cleaned, user.getId());
                return Optional.of(user);
            }
        }

        log.debug("{} ❌ No match found for phone={}", CLASS, rawPhone);
        return Optional.empty();
    }

    // ==========================================================
    // 📊 Logging helpers
    // ==========================================================

    /**
     * Utility to log a summary after processing a batch of contacts.
     */
    public static void logMatchSummary(int total, int matched, int unmatched) {
        log.info("{} 🧾 Processed {} contacts → Matched: {}, Unmatched: {}", CLASS, total, matched, unmatched);
    }

    // ==========================================================
    // 📱 Merge helpers
    // ==========================================================

    /**
     * Merge phone lists (by unique phone value).
     */
    public static List<ContactSyncRequest.PhoneEntry> mergePhoneLists(
            List<ContactSyncRequest.PhoneEntry> existing,
            List<ContactSyncRequest.PhoneEntry> incoming) {

        Map<String, ContactSyncRequest.PhoneEntry> map = new LinkedHashMap<>();
        if (existing != null) {
            existing.forEach(p -> map.put(p.getValue(), p));
        }
        if (incoming != null) {
            incoming.forEach(p -> map.putIfAbsent(p.getValue(), p));
        }
        log.debug("{} 🔄 Merged {} existing + {} incoming phone entries → {} unique",
                CLASS,
                existing != null ? existing.size() : 0,
                incoming != null ? incoming.size() : 0,
                map.size());
        return new ArrayList<>(map.values());
    }

    /**
     * Merge email lists (case-insensitive).
     */
    public static List<ContactSyncRequest.EmailEntry> mergeEmailLists(
            List<ContactSyncRequest.EmailEntry> existing,
            List<ContactSyncRequest.EmailEntry> incoming) {

        Map<String, ContactSyncRequest.EmailEntry> map = new LinkedHashMap<>();
        if (existing != null) {
            existing.forEach(e -> map.put(e.getValue().toLowerCase(Locale.ROOT), e));
        }
        if (incoming != null) {
            incoming.forEach(e -> map.putIfAbsent(e.getValue().toLowerCase(Locale.ROOT), e));
        }
        log.debug("{} 🔄 Merged {} existing + {} incoming email entries → {} unique",
                CLASS,
                existing != null ? existing.size() : 0,
                incoming != null ? incoming.size() : 0,
                map.size());
        return new ArrayList<>(map.values());
    }

    // ==========================================================
    // 📇 Sorting & Cache Helpers
    // ==========================================================

    /**
     * 🔄 Sort contacts with registered users first, alphabetically.
     */
    public static List<MatchedContactResponse> sortContacts(List<MatchedContactResponse> contacts) {
        return contacts.stream()
                .sorted((a, b) -> {
                    boolean aRegistered = a.getMatchedUserId() != null;
                    boolean bRegistered = b.getMatchedUserId() != null;

                    if (aRegistered && !bRegistered) return -1;
                    if (!aRegistered && bRegistered) return 1;

                    String nameA = Optional.ofNullable(a.getContactName()).orElse("").toLowerCase();
                    String nameB = Optional.ofNullable(b.getContactName()).orElse("").toLowerCase();
                    return nameA.compareTo(nameB);
                })
                .toList();
    }

    /**
     * 🗝️ Generate Redis-style cache keys.
     */
    public static String cacheKey(Long userId) {
        return "contacts:matched:" + userId;
    }

    public static String cacheKey(Long userId, String type) {
        return "contacts:" + type + ":user:" + userId;
    }
}
