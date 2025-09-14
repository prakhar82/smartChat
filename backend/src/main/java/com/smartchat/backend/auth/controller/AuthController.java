/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.auth.controller;


import com.smartchat.backend.auth.AuthService;
import com.smartchat.backend.auth.dto.AuthRequest;
import com.smartchat.backend.auth.dto.AuthResponse;
import com.smartchat.backend.auth.dto.RefreshRequest;
import com.smartchat.backend.auth.dto.RegisterRequest;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.GoogleContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepo;  // ✅ Inject repository here
    private final GoogleContactService googleContactService;

    // =========================
    // Register Endpoint
    // =========================

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(
                    new AuthResponse(null, null, null, "Passwords do not match")
            );
        }

        AuthResponse response = authService.register(request);

        // Sync Google contacts if provided
        if (request.getGoogleToken() != null && !request.getGoogleToken().isBlank()) {
            Long userId = response.getUserId();
            googleContactService.fetchAndSync(userId, request.getGoogleToken());
        }

        return ResponseEntity.ok(response);
    }


    // =========================
    // Login Endpoint
    // =========================
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse authResponse = authService.login(request.getMobileNumber(), request.getPassword());
        return ResponseEntity.ok(authResponse);
    }

    // =========================
    // Refresh Token Endpoint
    // =========================
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshRequest request) {
        AuthResponse authResponse = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

    /**
     * Return current authenticated user details.
     * The client can call this after login to refresh user profile.
     * Expects Authorization: Bearer &lt;token&gt;
     */
    @GetMapping("/me")
    public ResponseEntity<com.smartchat.backend.model.User> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.replace("Bearer ", "");
        // NOTE: rely on existing JWT service to extract subject (mobile number) if you have one.
        // Fallback: if token contains "sub" in payload, try to decode
        try {
            String[] parts = token.split("\\\\.");
            if (parts.length < 2) return ResponseEntity.status(401).build();
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(payload);
            String mobile = node.has("sub") ? node.get("sub").asText() : null;
            if (mobile == null) return ResponseEntity.status(401).build();
            return userRepo.findByMobileNumber(mobile)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

}
