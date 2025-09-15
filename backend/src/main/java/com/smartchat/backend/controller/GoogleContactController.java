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
 * Endpoint:
 *   POST /api/contacts/google/sync?userId=123
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
     * The mobile/web client obtains a Google OAuth access token via Google Sign-In,
     * then calls this endpoint with that token in the Authorization header:
     *  POST /api/contacts/google/sync?userId=...
     *  Header: Authorization: Bearer <googleAccessToken>
     *
     * Server uses the token to call Google People API and sync phone numbers.
     */

    /*@PostMapping("/sync")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> syncGoogleContacts(
            @RequestParam Long userId,
            @RequestBody Map<String, String> payload,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String accessToken = payload.get("accessToken");
            if (accessToken == null || accessToken.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Missing Google access token"));
            }

            // ✅ Now we call the service
            googleContactService.fetchAndSync(userId, accessToken);

            return ResponseEntity.ok(Map.of("status", "success", "message", "Google contacts synced"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }*/

    /**
     * 🔑 Sync Google contacts into UserContact table.
     * Request body: { "userId": 123, "accessToken": "ya29.a0Af..." }
     */
    @PostMapping("/google/sync")
    public ResponseEntity<?> syncGoogleContacts(@RequestBody Map<String, String> payload) {
        try {
            Long userId = Long.parseLong(payload.get("userId"));
            String accessToken = payload.get("accessToken");

            if (accessToken == null || accessToken.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "accessToken is required"));
            }

            googleContactService.fetchAndSync(userId, accessToken);

            return ResponseEntity.ok(Map.of("status", "success", "message", "Google contacts synced"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }


    /**
     * Send invite email using inviter's Google OAuth token.
     * Body: { "contactEmail": "...", "contactName": "..." }
     */
    @PostMapping("/invite/send")
    public ResponseEntity<?> sendInviteEmail(
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String contactEmail = payload.get("contactEmail");
        String contactName = payload.getOrDefault("contactName", "");
        if (contactEmail == null || contactEmail.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "contactEmail is required"));
        }

        // ✅ Extract inviter ID from JWT
        Long inviterUserId = extractUserId(authHeader);

        // ✅ Get Google OAuth access token
        String accessToken = googleOAuthService.getValidAccessToken(inviterUserId);
        if (accessToken == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "google_sync_required"));
        }

        // Prepare invite link
        String inviteToken = UUID.randomUUID().toString();
        // ✅ Persist invite token
        InviteToken tokenEntity = InviteToken.builder()
                .token(inviteToken)
                .inviter(userRepository.findById(inviterUserId).orElseThrow())
                .createdAt(Instant.now())
                .used(false)
                .build();
        inviteTokenRepository.save(tokenEntity);

        String inviteLink = "/register?ref=" + inviteToken;

        // Resolve inviter email
        String inviterEmail = userRepository.findById(inviterUserId)
                .map(u -> u.getEmail() != null ? u.getEmail() : "no-reply@smartchat.local")
                .orElse("no-reply@smartchat.local");

        // Compose email
        String subject = "[SmartChat] Join me on SmartChat";
        String body = String.format(
                "Hi %s,\n\nI am using SmartChat — a secure Indian messaging app.\n" +
                        "Join here: %s\n\n- Sent via SmartChat",
                contactName.isBlank() ? "there" : contactName, inviteLink
        );

        // Send email via Gmail
        boolean ok = gmailService.sendEmail(accessToken, inviterEmail, contactEmail, subject, body);
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
