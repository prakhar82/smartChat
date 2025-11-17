/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * ============================================================================
 *  SmartChat - ContactController (Production Ready, No /api Prefix)
 * ----------------------------------------------------------------------------
 *  Handles all user contact operations:
 *   • Upload & sync contacts
 *   • Fetch matched SmartChat users
 *   • Check if user has contacts
 *
 *  JWT resolution delegated to AuthContract via API Gateway.
 *  This version assumes Gateway strips "/api" prefix.
 * ----------------------------------------------------------------------------
 *  © 2025 SmartChat Contributors. All Rights Reserved.
 * ============================================================================
 */

package com.smartchat.contact.controller;

import com.smartchat.common.contracts.AuthContract;
import com.smartchat.common.dto.MatchedContactResponse;
import com.smartchat.common.dto.UserDTO;
import com.smartchat.contact.dto.ContactSyncRequest;
import com.smartchat.contact.service.ContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/contact") // ✅ Gateway already strips `/api`
@RequiredArgsConstructor
public class ContactController {

    private static final String CLASS = "[ContactController]";
    private final ContactService contactService;
    private final AuthContract authContract;

    // ==========================================================
    // 📤 1. Sync uploaded contacts (manual/device)
    // ==========================================================
    @PostMapping("/sync")
    public ResponseEntity<?> syncContacts(
            @RequestBody ContactSyncRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            log.warn("{} ⚠️ Sync aborted — missing or invalid JWT", CLASS);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "Invalid or missing token"));
        }

        request.setOwnerUserId(userId);
        log.info("{} ▶ Sync request for userId={} with {} contacts", CLASS,
                userId, request.getContacts() != null ? request.getContacts().size() : 0);

        try {
            contactService.syncContacts(request);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            log.error("{} ❌ Failed to sync contacts for userId={} → {}", CLASS, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "Failed to sync contacts",
                            "details", e.getMessage()
                    ));
        }
    }

    // ==========================================================
    // 📥 2. Fetch matched contacts
    // ==========================================================
    @GetMapping("/matched")
    public ResponseEntity<?> getMatchedContacts(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            log.warn("{} ⚠️ Unauthorized /matched request", CLASS);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "Invalid or missing token"));
        }

        log.info("{} 🧠 Fetching matched contacts for userId={}", CLASS, userId);
        try {
            List<MatchedContactResponse> matched = contactService.getMatchedContacts(userId);
            log.debug("{} ✅ Returning {} matched contacts", CLASS, matched.size());
            return ResponseEntity.ok(matched);
        } catch (Exception e) {
            log.error("{} ❌ Error fetching matched contacts for userId={} → {}", CLASS, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal Server Error", "message", e.getMessage()));
        }
    }

    // ==========================================================
    // 📊 3. Check if user has contacts
    // ==========================================================
    @GetMapping("/has-contacts")
    public ResponseEntity<?> hasContacts(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            log.warn("{} ⚠️ Unauthorized /has-contacts request", CLASS);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("hasContacts", false));
        }

        boolean hasContacts = contactService.userHasContacts(userId);
        log.info("{} 🔍 hasContacts for userId={} → {}", CLASS, userId, hasContacts);
        return ResponseEntity.ok(Map.of("hasContacts", hasContacts));
    }

    // ==========================================================
    // 🧩 Utility: Resolve userId from JWT (via AuthContract)
    // ==========================================================
    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("{} 🚫 Missing or malformed Authorization header", CLASS);
            return null;
        }

        try {
            String jwt = authHeader.substring(7);
            UserDTO user = authContract.getUserByToken(jwt);
            if (user == null || user.getId() == null) {
                log.warn("{} ⚠️ Invalid JWT token (no user)", CLASS);
                return null;
            }

            Long userId = Long.parseLong(user.getId());
            log.debug("{} 🔑 Resolved userId={} from token", CLASS, userId);
            return userId;
        } catch (Exception e) {
            log.error("{} ❌ JWT resolution failed → {}", CLASS, e.getMessage(), e);
            return null;
        }
    }
}
