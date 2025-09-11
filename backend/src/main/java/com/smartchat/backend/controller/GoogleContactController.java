package com.smartchat.backend.controller;

import com.smartchat.backend.service.GoogleContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public void syncGoogleContacts(@RequestParam Long userId,
                                   @RequestHeader("Authorization") String authHeader) {
        String token = authHeader != null ? authHeader.replace("Bearer ", "") : null;
        if (token != null && !token.isBlank()) {
            googleContactService.fetchAndSync(userId, token);
        }
    }
}
