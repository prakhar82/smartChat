/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.auth.JwtUtil;
import com.smartchat.backend.dto.ContactSyncRequest;
import com.smartchat.backend.dto.MatchedContactResponse;
import com.smartchat.backend.repository.jpa.UserRepository;
import com.smartchat.backend.service.ContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * [ContactController]
 * <p>
 * Handles all contact-related endpoints for SmartChat.
 * <p>
 * ✅ Syncs uploaded contacts
 * ✅ Returns matched contact list (cached or DB)
 * ✅ Checks if user has any contacts
 * ✅ Resolves user from JWT token (supports both mobile + email)
 */
@Slf4j
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    private static final String CLASS = "[ContactController]";

    // ==========================================================
    // 📤 Sync uploaded contacts (manual or device)
    // ==========================================================
    @PostMapping("/sync")
    public ResponseEntity<?> syncContacts(
            @RequestBody ContactSyncRequest request,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            log.warn("{} ⚠️ Sync aborted — could not resolve userId from token", CLASS);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }

        // ✅ Always enforce JWT userId
        request.setOwnerUserId(userId);
        log.info("{} ▶ Received contact sync request for userId={}", CLASS, userId);

        contactService.syncContacts(request);
        log.info("{} ✅ Contacts synced successfully for userId={}", CLASS, userId);

        return ResponseEntity.ok(Map.of("status", "success"));
    }

    // ==========================================================
    // 📥 Fetch matched contacts
    // ==========================================================
    @GetMapping("/matched")
    public ResponseEntity<List<MatchedContactResponse>> getMatchedContacts(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            log.warn("{} ⚠️ Could not resolve user from token — returning []", CLASS);
            return ResponseEntity.ok(List.of());
        }

        log.info("{} 🧠 Fetching matched contacts for userId={}", CLASS, userId);
        List<MatchedContactResponse> matched = contactService.getMatchedContacts(userId);

        log.info("{} ✅ Returning {} matched contacts for userId={}", CLASS, matched.size(), userId);
        return ResponseEntity.ok(matched);
    }

    // ==========================================================
    // 📊 Check if user has any contacts
    // ==========================================================
    @GetMapping("/has-contacts")
    public ResponseEntity<Map<String, Boolean>> hasContacts(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("hasContacts", false));
        }

        boolean hasContacts = contactService.userHasContacts(userId);
        log.info("{} 🔍 hasContacts check for userId={} → {}", CLASS, userId, hasContacts);

        return ResponseEntity.ok(Map.of("hasContacts", hasContacts));
    }

    // ==========================================================
    // 🧩 Helper: Robust user resolver (mobile OR email)
    // ==========================================================
    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("{} ⚠️ Missing or invalid Authorization header", CLASS);
            return null;
        }

        try {
            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);

            if (username == null || username.isBlank()) {
                log.warn("{} ⚠️ Token missing username", CLASS);
                return null;
            }

            // 🔍 Try both mobile and email for maximum compatibility
            return userRepository.findByMobileNumber(username)
                    .or(() -> userRepository.findByEmail(username))
                    .map(user -> {
                        log.debug("{} 🔑 Resolved userId={} for principal={}", CLASS, user.getId(), username);
                        return user.getId();
                    })
                    .orElseGet(() -> {
                        log.warn("{} ⚠️ No matching user found for principal={}", CLASS, username);
                        return null;
                    });

        } catch (Exception e) {
            log.error("{} ❌ Failed to resolve userId from token: {}", CLASS, e.getMessage());
            return null;
        }
    }
}
