/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.auth.controller;

import com.smartchat.backend.auth.AuthService;
import com.smartchat.backend.auth.JwtUtil;
import com.smartchat.backend.auth.dto.AuthRequest;
import com.smartchat.backend.auth.dto.AuthResponse;
import com.smartchat.backend.auth.dto.RefreshRequest;
import com.smartchat.backend.auth.dto.RegisterRequest;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.GoogleContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepo;
    private final GoogleContactService googleContactService;
    private final JwtUtil jwtUtil;

    // =========================
    // Register Endpoint
    // =========================
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("[AuthController] Incoming registration request for email={} mobile={}",
                request.getEmail(), request.getMobileNumber());

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("[AuthController] ❌ Passwords do not match for mobile={}", request.getMobileNumber());
            return ResponseEntity.badRequest().body(
                    new AuthResponse(null, null, null, "Passwords do not match")
            );
        }

        AuthResponse response = authService.register(request);
        log.info("[AuthController] ✅ Registered userId={} mobile={}", response.getUserId(), request.getMobileNumber());

        // Optionally sync Google contacts if token is provided
        if (request.getGoogleToken() != null && !request.getGoogleToken().isBlank()) {
            try {
                log.info("[AuthController] Syncing Google contacts for userId={}", response.getUserId());
                googleContactService.fetchAndSync(response.getUserId(), request.getGoogleToken());
            } catch (Exception e) {
                log.error("[AuthController] Failed to sync Google contacts for userId={}", response.getUserId(), e);
            }
        }

        return ResponseEntity.ok(response);
    }

    // =========================
    // Login Endpoint
    // =========================
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.info("[AuthController] Login attempt for mobile={}", request.getMobileNumber());
        AuthResponse authResponse = authService.login(request.getMobileNumber(), request.getPassword());
        log.info("[AuthController] ✅ Login successful for userId={}", authResponse.getUserId());
        return ResponseEntity.ok(authResponse);
    }

    // =========================
    // Refresh Token Endpoint
    // =========================
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshRequest request) {
        log.debug("[AuthController] Refreshing token...");
        AuthResponse authResponse = authService.refreshToken(request.getRefreshToken());
        log.info("[AuthController] ✅ Token refreshed for userId={}", authResponse.getUserId());
        return ResponseEntity.ok(authResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing or invalid token"));
        }

        String token = authHeader.substring(7);
        try {
            String username = jwtUtil.extractUsername(token);

            return userRepo.findByMobileNumber(username)
                    .map(ResponseEntity::ok)  // ✅ return User entity directly
                    .orElse(ResponseEntity.notFound().build());

        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
    }


}
