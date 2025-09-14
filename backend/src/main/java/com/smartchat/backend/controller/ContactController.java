/*
 * ContactController
 *
 * Purpose:
 *  - /api/contacts/sync  → accept device & Google contacts and persist for matching
 *  - /api/contacts/matched?userId=... → return matched SmartChat users
 *  - /api/contacts/invite/send → send Gmail invites
 *
 * Called from:
 *  - Angular/Capacitor frontend after login/register
 */

package com.smartchat.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartchat.backend.dto.ContactSyncRequest;
import com.smartchat.backend.dto.MatchedContactResponse;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.ContactService;
import com.smartchat.backend.service.GoogleOAuthService;
import com.smartchat.backend.service.GmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final GoogleOAuthService googleOAuthService;
    private final GmailService gmailService;
    private final UserRepository userRepository;

    @PostMapping("/sync")
    public void syncContacts(@RequestBody ContactSyncRequest request) {
        contactService.syncContacts(request);
    }

    @GetMapping("/matched")
    public List<MatchedContactResponse> getMatchedContacts(@RequestParam Long userId) {
        return contactService.getMatchedContacts(userId);
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

        // Derive inviter user id from JWT
        Long inviterUserId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.replace("Bearer ", "");
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payloadPart = new String(Base64.getUrlDecoder().decode(parts[1]));
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(payloadPart);
                    inviterUserId = node.has("sub") ? node.get("sub").asLong() : null;
                }
            } catch (Exception ignored) {}
        }
        if (inviterUserId == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Unauthorized - missing inviter id in token"));
        }

        // Get Google OAuth access token
        String accessToken = googleOAuthService.getValidAccessToken(inviterUserId);
        if (accessToken == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "google_sync_required"));
        }

        // Prepare invite link
        String inviteToken = UUID.randomUUID().toString();
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
}
