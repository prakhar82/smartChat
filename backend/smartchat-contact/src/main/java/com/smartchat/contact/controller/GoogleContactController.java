/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.controller;

import com.smartchat.common.contracts.AuthContract;
import com.smartchat.common.dto.UserDTO;
import com.smartchat.contact.service.GmailService;
import com.smartchat.contact.service.GoogleContactService;
import com.smartchat.contact.service.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * ==========================================================
 * [GoogleContactController]
 * ----------------------------------------------------------
 * Handles Google contact-related operations:
 * ✅ Google contact synchronization
 * ✅ Sending invite emails via Gmail API
 * ✅ Integrates with GoogleOAuthService for token validation
 * <p>
 * Endpoints:
 * - POST /api/contacts/google/sync        → Fetch & sync contacts from Google
 * - POST /api/contacts/google/invite/send → Send invite emails using Gmail API
 * ==========================================================
 */
@Slf4j
@RestController
@RequestMapping("/contacts/google")
@RequiredArgsConstructor
public class GoogleContactController {

    private static final String CLASS = "[GoogleContactController]";

    private final GoogleContactService googleContactService;
    private final GmailService gmailService;
    private final GoogleOAuthService googleOAuthService;
    private final AuthContract authContract;

    // ==========================================================
    // 🟢 1. Sync Google Contacts
    // ==========================================================

    /**
     * Synchronizes a user's Google contacts with SmartChat.
     * <p>
     * Request Body:
     * {
     * "access_token": "<Google OAuth token>"
     * }
     * <p>
     * Flow:
     * 1️⃣ Validates JWT principal
     * 2️⃣ Resolves user via AuthContract
     * 3️⃣ Calls GoogleContactService.fetchAndSync()
     */
    @PostMapping("/sync")
    public ResponseEntity<?> syncGoogleContacts(
            @RequestBody Map<String, String> body,
            Principal principal
    ) {
        log.info("{} ▶ Received request to sync Google contacts", CLASS);

        try {
            Long userId = extractUserId(principal);
            String accessToken = body.get("access_token");

            if (accessToken == null || accessToken.isBlank()) {
                log.warn("{} ⚠️ Missing access_token in request", CLASS);
                return ResponseEntity.badRequest().body(Map.of("error", "Missing access_token"));
            }

            googleContactService.fetchAndSync(userId, accessToken);
            log.info("{} ✅ Successfully synced Google contacts for userId={}", CLASS, userId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "userId", userId,
                    "timestamp", Instant.now().toString()
            ));
        } catch (Exception e) {
            log.error("{} ❌ Failed to sync Google contacts: {}", CLASS, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to sync contacts",
                    "details", e.getMessage()
            ));
        }
    }

    // ==========================================================
    // ✉️ 2. Send Invite Email via Gmail API
    // ==========================================================

    /**
     * Sends a SmartChat invitation email via Gmail API using the inviter’s OAuth token.
     * <p>
     * Request Body:
     * {
     * "contactEmail": "friend@example.com",
     * "contactName": "John Doe"
     * }
     * <p>
     * Flow:
     * 1️⃣ Resolves inviter via JWT principal
     * 2️⃣ Retrieves valid Google OAuth token
     * 3️⃣ Generates unique invite link
     * 4️⃣ Sends Gmail message via GmailService
     */
    @PostMapping("/invite/send")
    public ResponseEntity<?> sendInviteEmail(
            @RequestBody Map<String, String> payload,
            Principal principal
    ) {
        final String method = CLASS + " /invite/send";
        try {
            log.info("{} ▶ Received invite request: {}", method, payload);

            String contactEmail = payload.get("contactEmail");
            String contactName = payload.getOrDefault("contactName", "");

            if (contactEmail == null || contactEmail.isBlank()) {
                log.warn("{} ❌ Missing contactEmail in payload", method);
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "contactEmail is required"
                ));
            }

            // ✅ Identify inviter
            Long inviterUserId = extractUserId(principal);
            UserDTO inviter = authContract.getUserById(String.valueOf(inviterUserId));
            if (inviter == null) {
                log.error("{} ❌ Could not resolve inviter for userId={}", method, inviterUserId);
                return ResponseEntity.status(401).body(Map.of(
                        "status", "error",
                        "message", "User not found"
                ));
            }

            // ✅ Retrieve valid Google access token
            String accessToken = googleOAuthService.getValidAccessToken(inviterUserId);
            if (accessToken == null) {
                log.warn("{} ⚠️ No valid Google token found for inviterUserId={}", method, inviterUserId);
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "google_sync_required"
                ));
            }

            // ✅ Generate invite link (could use InviteTokenDTO or ephemeral link)
            String inviteTokenValue = UUID.randomUUID().toString();
            String inviteLink = String.format(
                    "https://smartchat.app/register?ref=%s", inviteTokenValue
            );

            log.info("{} 🔗 Generated invite link: {}", method, inviteLink);

            // ✅ Prepare and send email
            String inviterEmail = inviter.getEmail() != null
                    ? inviter.getEmail()
                    : "no-reply@smartchat.local";

            String subject = "[SmartChat] Join me on SmartChat!";
            String messageBody = String.format("""
                            Hi %s,

                            I'm inviting you to join me on SmartChat — a secure Indian messaging app.
                            You can sign up using the link below:
                                                
                            %s

                            Sent via SmartChat 💬
                            """,
                    (contactName.isBlank() ? "there" : contactName),
                    inviteLink
            );

            boolean success = gmailService.sendEmail(
                    accessToken,
                    "me",
                    contactEmail,
                    subject,
                    messageBody
            );

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

    // ==========================================================
    // 🧩 Utility — Extract SmartChat userId
    // ==========================================================

    /**
     * Extracts the SmartChat userId from Principal.
     * Uses AuthContract for resolution, not direct repository.
     */
    private Long extractUserId(Principal principal) {
        if (principal == null) {
            log.error("{} ❌ Principal is null", CLASS);
            throw new RuntimeException("Unauthorized: missing principal");
        }

        String principalValue = principal.getName();
        log.debug("{} 🔍 Resolving user from principal='{}'", CLASS, principalValue);

        try {
            UserDTO user = authContract.getUserByToken(principalValue);
            if (user != null) {
                log.info("{} ✅ Resolved userId={} via token", CLASS, user.getId());
                return Long.parseLong(user.getId());
            }

            // fallback by direct ID or username (if applicable)
            user = authContract.getUserById(principalValue);
            if (user != null) {
                return Long.parseLong(user.getId());
            }

        } catch (Exception e) {
            log.error("{} ❌ Failed to resolve user from principal={} → {}", CLASS, principalValue, e.getMessage());
        }

        throw new RuntimeException("User not found for principal");
    }
}
