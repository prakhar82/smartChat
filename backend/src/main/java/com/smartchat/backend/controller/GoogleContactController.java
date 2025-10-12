/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.auth.JwtUtil;
import com.smartchat.backend.model.InviteToken;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.jpa.InviteTokenRepository;
import com.smartchat.backend.repository.jpa.UserRepository;
import com.smartchat.backend.service.GmailService;
import com.smartchat.backend.service.GoogleContactService;
import com.smartchat.backend.service.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * [GoogleContactController]
 * ------------------------------------------------------------
 * Handles all Google contact-related APIs in SmartChat:
 * ✅ Google contact synchronization
 * ✅ Sending invite emails via Gmail API
 * ✅ Integrates with GoogleOAuthService for token validation
 * <p>
 * Endpoints:
 * - POST /api/contacts/google/sync        → Fetch & sync contacts from Google
 * - POST /api/contacts/google/invite/send → Send invite emails using Gmail API
 */
@Slf4j
@RestController
@RequestMapping("/api/contacts/google")
@RequiredArgsConstructor
public class GoogleContactController {

    private final GoogleContactService googleContactService;
    private final GmailService gmailService;
    private final InviteTokenRepository inviteTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final GoogleOAuthService googleOAuthService;

    private static final String CLASS = "[GoogleContactController]";

    // ======================================================================
    // 🟢 1. Sync Google Contacts
    // ======================================================================

    /**
     * Synchronizes a user's Google contacts with SmartChat.
     * <p>
     * Request Body:
     * {
     * "access_token": "<Google OAuth token>"
     * }
     * <p>
     * Headers:
     * Authorization: Bearer <SmartChat JWT>
     * <p>
     * Steps:
     * 1️⃣ Validates JWT & resolves userId
     * 2️⃣ Calls GoogleContactService.fetchAndSync()
     * 3️⃣ Returns confirmation or detailed error
     */

    @PostMapping("/sync")
    public ResponseEntity<?> syncGoogleContacts(@RequestBody Map<String, String> body, Principal principal) {
        log.info("[GoogleContactController] /sync ▶ Request received to sync Google contacts");

        Long userId = extractUserId(principal);
        log.debug("[GoogleContactController] ✅ Found userId={} from principal={}", userId, principal.getName());

        String accessToken = body.get("access_token");

        if (accessToken == null || accessToken.isBlank()) {
            log.warn("[GoogleContactController] ⚠️ Missing access_token in request");
            return ResponseEntity.badRequest().body(Map.of("error", "Missing access_token"));
        }

        try {
            googleContactService.fetchAndSync(userId, accessToken);
            log.info("[GoogleContactController] ✅ Google contacts for userId={}", userId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "userId", userId
            ));
        } catch (Exception e) {
            log.error("[GoogleContactController] ❌ Failed to sync Google contacts: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to sync contacts",
                    "details", e.getMessage()
            ));
        }
    }


    // ======================================================================
    // ✉️ 2. Send Invite Email via Gmail API
    // ======================================================================

    /**
     * Sends a SmartChat invitation email via Gmail API using the inviter’s OAuth token.
     * <p>
     * Request Body:
     * {
     * "contactEmail": "friend@example.com",
     * "contactName": "John Doe"
     * }
     * <p>
     * Steps:
     * 1️⃣ Resolves inviter via JWT
     * 2️⃣ Retrieves valid Google OAuth token
     * 3️⃣ Generates unique invite link
     * 4️⃣ Sends Gmail message via GmailService
     */
    @PostMapping("/invite/send")
    public ResponseEntity<?> sendInviteEmail(
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        final String method = CLASS + " /invite/send";
        try {
            log.info("{} ▶ Received request to send invite: {}", method, payload);

            // 🔹 Extract email + name
            String contactEmail = payload.get("contactEmail");
            String contactName = payload.getOrDefault("contactName", "");

            if (contactEmail == null || contactEmail.isBlank()) {
                log.warn("{} ❌ Missing contactEmail in payload", method);
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "contactEmail is required"
                ));
            }

            // 🔹 Identify inviter
            Long inviterUserId = extractUserId(authHeader);
            log.info("{} 👤 Inviter identified: userId={}", method, inviterUserId);

            // 🔹 Retrieve valid Google access token
            String accessToken = googleOAuthService.getValidAccessToken(inviterUserId);
            if (accessToken == null) {
                log.warn("{} ⚠️ No valid Google token found for inviterUserId={}", method, inviterUserId);
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "google_sync_required"
                ));
            }

            // 🔹 Generate invite link with unique token
            String inviteTokenValue = UUID.randomUUID().toString();
            InviteToken inviteToken = InviteToken.builder()
                    .token(inviteTokenValue)
                    .inviter(userRepository.findById(inviterUserId).orElseThrow())
                    .createdAt(Instant.now())
                    .used(false)
                    .build();
            inviteTokenRepository.save(inviteToken);

            String inviteLink = "/register?ref=" + inviteTokenValue;
            log.info("{} 🔗 Generated invite link: {}", method, inviteLink);

            // 🔹 Prepare email details
            String inviterEmail = userRepository.findById(inviterUserId)
                    .map(u -> u.getEmail() != null ? u.getEmail() : "no-reply@smartchat.local")
                    .orElse("no-reply@smartchat.local");

            String subject = "[SmartChat] Join me on SmartChat!";
            String body = String.format(
                    "Hi %s,\n\nI'm using SmartChat — a secure Indian messaging app.\n" +
                            "You can join me here: %s\n\n- Sent via SmartChat\n",
                    (contactName.isBlank() ? "there" : contactName),
                    inviteLink
            );

            // 🔹 Send email via Gmail API
            boolean success = gmailService.sendEmail(accessToken, "me", contactEmail, subject, body);

            if (success) {
                log.info("{} ✅ Invite email sent successfully to {} by userId={}", method, contactEmail, inviterUserId);
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Invite sent successfully"
                ));
            } else {
                log.error("{} ❌ Gmail API failed to send invite to {}", method, contactEmail);
                return ResponseEntity.internalServerError().body(Map.of(
                        "status", "error",
                        "message", "Failed to send invite via Gmail"
                ));
            }

        } catch (Exception e) {
            log.error("{} ❌ Exception while sending invite: {}", method, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to send invite email",
                    "details", e.getMessage()
            ));
        }
    }

    // ======================================================================
    // 🧩 Utility — Extract SmartChat userId from JWT
    // ======================================================================

    /**
     * Extracts the SmartChat userId from Authorization header (JWT).
     * Throws RuntimeException if invalid or user not found.
     */


    private Long extractUserId(String authHeader) {
        final String method = CLASS + " extractUserId";
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.error("{} ❌ Missing or invalid Authorization header", method);
            throw new RuntimeException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);

        return userRepository.findByMobileNumber(username)
                .map(u -> {
                    log.debug("{} ✅ Extracted userId={} for principal={}", method, u.getId(), username);
                    return u.getId();
                })
                .orElseThrow(() -> {
                    log.error("{} ❌ No user found for principal={}", method, username);
                    return new RuntimeException("User not found for token principal");
                });
    }

    /**
     * ==========================================================
     * 🔹 Extract userId safely from Principal (mobile or email)
     * ==========================================================
     */
    private Long extractUserId(Principal principal) {
        if (principal == null) {
            log.error("[GoogleContactController] extractUserId ❌ Principal is null");
            throw new RuntimeException("Unauthorized: missing principal");
        }

        String principalValue = principal.getName();
        log.debug("[GoogleContactController] 🔍 Resolving user from principal='{}'", principalValue);

        return userRepository.findByMobileNumber(principalValue)
                .or(() -> userRepository.findByEmail(principalValue))
                .map(User::getId)
                .orElseThrow(() -> {
                    log.error("[GoogleContactController] extractUserId ❌ No user found for principal={}", principalValue);
                    return new RuntimeException("User not found for token principal");
                });
    }
}
