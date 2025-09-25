/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * GoogleContactController (Handles Google contacts only.)
 *
 * Purpose:
 *   Expose endpoint to sync Google contacts with SmartChat backend.
 *   - Requires SmartChat JWT in Authorization header
 *   - Requires Google OAuth access token in request body
 *
 * Endpoints:
 *   POST /api/contacts/google/sync
 *   POST /api/contacts/google/invite/send
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.auth.JwtUtil;
import com.smartchat.backend.model.InviteToken;
import com.smartchat.backend.repository.InviteTokenRepository;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.GmailService;
import com.smartchat.backend.service.GoogleContactService;
import com.smartchat.backend.service.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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

    /**
     * Sync Google contacts with SmartChat backend.
     * <p>
     * Request:
     * POST /api/contacts/google/sync
     * Header: Authorization: Bearer <SmartChatJWT>
     * Body: { "accessToken": "<googleAccessToken>" }
     */
    @PostMapping("/sync")
    public ResponseEntity<?> syncGoogleContacts(
            @RequestBody Map<String, String> payload,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String accessToken = payload.get("accessToken");
            if (accessToken == null || accessToken.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "accessToken is required"));
            }

            Long userId = extractUserId(authHeader);
            googleContactService.fetchAndSync(userId, accessToken);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Google contacts synced"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Send invite email using inviter’s Google OAuth token.
     * <p>
     * Request:
     * POST /api/contacts/google/invite/send
     * Header: Authorization: Bearer <SmartChatJWT>
     * Body: { "contactEmail": "...", "contactName": "..." }
     */
    @PostMapping("/invite/send")
    public ResponseEntity<?> sendInviteEmail(
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String contactEmail = payload.get("contactEmail");
        String contactName = payload.getOrDefault("contactName", "");
        if (contactEmail == null || contactEmail.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "contactEmail is required"));
        }

        Long inviterUserId = extractUserId(authHeader);

        // Fetch Google access token from DB (from last sync)
        String accessToken = googleOAuthService.getValidAccessToken(inviterUserId);
        if (accessToken == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "google_sync_required"));
        }

        // Prepare invite token
        String inviteToken = UUID.randomUUID().toString();
        InviteToken tokenEntity = InviteToken.builder()
                .token(inviteToken)
                .inviter(userRepository.findById(inviterUserId).orElseThrow())
                .createdAt(Instant.now())
                .used(false)
                .build();
        inviteTokenRepository.save(tokenEntity);

        String inviteLink = "/register?ref=" + inviteToken;

        // Fetch inviter email (for display only)
        String inviterEmail = userRepository.findById(inviterUserId)
                .map(u -> u.getEmail() != null ? u.getEmail() : "no-reply@smartchat.local")
                .orElse("no-reply@smartchat.local");

        // Email content (append inviter info)
        String subject = "[SmartChat] Join me on SmartChat";
        String body = String.format(
                "Hi %s,\n\nI am using SmartChat — a secure Indian messaging app.\n" +
                        "Join here: %s\n\n" +
                        "Invited by: %s\n\n" +
                        "- Sent via SmartChat",
                contactName.isBlank() ? "there" : contactName, inviteLink, inviterEmail
        );

        // Always use "me" as sender
        boolean ok = gmailService.sendEmail(accessToken, "me", contactEmail, subject, body);

        if (ok) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Invite sent"));
        } else {
            return ResponseEntity.status(500)
                    .body(Map.of("status", "error", "message", "Failed to send invite via Gmail"));
        }
    }


    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing token");
        }
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        return userRepository.findByMobileNumber(username)
                .map(u -> u.getId())
                .orElseThrow(() -> new RuntimeException("User not found for token"));
    }
}
