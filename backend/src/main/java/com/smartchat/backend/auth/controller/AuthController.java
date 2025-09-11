/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

package com.smartchat.backend.auth.controller;


import com.smartchat.backend.auth.AuthService;
import com.smartchat.backend.auth.dto.AuthRequest;
import com.smartchat.backend.auth.dto.AuthResponse;
import com.smartchat.backend.auth.dto.RefreshRequest;
import com.smartchat.backend.auth.dto.RegisterRequest;
import com.smartchat.backend.repository.UserRepository;
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


    // =========================
    // Register Endpoint
    // =========================

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
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
