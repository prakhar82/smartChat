/*
 * GoogleContactController
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

import com.smartchat.backend.service.GoogleContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contacts/google")
@RequiredArgsConstructor
public class GoogleContactController {

    private final GoogleContactService googleContactService;

    /**
     * The mobile/web client obtains a Google OAuth access token via Google Sign-In,
     * then calls this endpoint with that token in the Authorization header:
     *  POST /api/contacts/google/sync?userId=...
     *  Header: Authorization: Bearer <googleAccessToken>
     *
     * Server uses the token to call Google People API and sync phone numbers.
     */

    @PostMapping("/sync")
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
    }
}
