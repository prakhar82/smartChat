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
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.jpa.UserRepository;
import com.smartchat.backend.service.GoogleContactService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ==========================================================
 * ✅ AuthController
 * ----------------------------------------------------------
 * Handles authentication:
 * - Registration
 * - Login
 * - Refresh token
 * - Current user profile
 * <p>
 * Integrates with enhanced JwtUtil → now includes email & mobile.
 * ==========================================================
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String CLASS = "[AuthController]";

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepo;
    private final GoogleContactService googleContactService;

    // ==========================================================
    // 🔹 Register a new user
    // ==========================================================
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("{} ▶ Registering new user (email={}, mobile={})", CLASS, request.getEmail(), request.getMobileNumber());

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("{} ❌ Passwords do not match for mobile={}", CLASS, request.getMobileNumber());
            return ResponseEntity.badRequest().body(
                    new AuthResponse(null, null, null, "Passwords do not match")
            );
        }

        AuthResponse response = authService.register(request);
        log.info("{} ✅ Registered successfully → userId={} mobile={}", CLASS, response.getUserId(), request.getMobileNumber());

        // Optional: auto-sync Google contacts if Google token is provided
        if (request.getGoogleToken() != null && !request.getGoogleToken().isBlank()) {
            try {
                log.info("{} 🔄 Starting Google contact sync for userId={}", CLASS, response.getUserId());
                googleContactService.fetchAndSync(response.getUserId(), request.getGoogleToken());
            } catch (Exception e) {
                log.error("{} ⚠️ Google contact sync failed for userId={} → {}", CLASS, response.getUserId(), e.getMessage());
            }
        }

        return ResponseEntity.ok(response);
    }

    // ==========================================================
    // 🔹 Login user
    // ==========================================================
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.info("{} ▶ Login attempt for mobile={}", CLASS, request.getMobileNumber());

        try {
            AuthResponse authResponse = authService.login(request.getMobileNumber(), request.getPassword());

            // Enrich JWT with both email & mobile if available
            Optional<User> userOpt = userRepo.findByMobileNumber(request.getMobileNumber());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                String email = user.getEmail();
                String mobile = user.getMobileNumber();

                // Generate updated access & refresh tokens with enhanced claims
                String accessToken = jwtUtil.generateAccessToken(
                        user.getId(),
                        email != null ? email : mobile,  // subject
                        email,
                        mobile,
                        user.getRolesAsList() // assuming getRolesAsList() returns List<String>
                );

                String refreshToken = jwtUtil.generateRefreshToken(
                        user.getId(),
                        email != null ? email : mobile,
                        email,
                        mobile
                );

                authResponse.setAuthToken(accessToken);
                authResponse.setRefreshToken(refreshToken);
                authResponse.setUserId(user.getId());
            }

            log.info("{} ✅ Login successful → userId={}", CLASS, authResponse.getUserId());
            return ResponseEntity.ok(authResponse);

        } catch (Exception e) {
            log.error("{} ❌ Login failed for mobile={} → {}", CLASS, request.getMobileNumber(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, null, "Invalid credentials"));
        }
    }


    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(HttpServletRequest request) {
        try {
            String token = jwtUtil.extractTokenFromRequest(request);
            Long userId = jwtUtil.extractUserId(token);
            String username = jwtUtil.extractUsername(token);
            String email = jwtUtil.extractEmail(token);
            String mobile = jwtUtil.extractMobile(token);

            log.info("[AuthController] ▶ Fetching profile for userId={} (email={}, mobile={})", userId, email, mobile);

            Optional<User> userOpt = Optional.empty();
            if (userId != null) {
                userOpt = userRepo.findById(userId);
            }
            if (userOpt.isEmpty() && email != null) {
                userOpt = userRepo.findByEmail(email);
            }
            if (userOpt.isEmpty() && mobile != null) {
                userOpt = userRepo.findByMobileNumber(mobile);
            }

            if (userOpt.isEmpty()) {
                log.warn("[AuthController] ⚠️ No user found for token principal={}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();

            Map<String, Object> profile = new HashMap<>();
            profile.put("userId", user.getId());
            profile.put("email", user.getEmail());
            profile.put("username", username);
            profile.put("countryCode", user.getCountryCode());
            profile.put("mobileNumber", user.getMobileNumber());
            profile.put("firstName", user.getFirstName());
            profile.put("lastName", user.getLastName());
            profile.put("tokenExpires", jwtUtil.extractExpiration(token));

            log.info("[AuthController] ✅ Returning enriched profile for userId={} → {}", user.getId(), profile);
            return ResponseEntity.ok(profile);

        } catch (Exception e) {
            log.error("[AuthController] ❌ Failed to return profile", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
        }
    }


    // ==========================================================
    // 🔹 Refresh token
    // ==========================================================
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshRequest request) {
        log.info("{} ▶ Refresh token requested", CLASS);

        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            log.warn("{} ❌ Missing refresh_token in request body", CLASS);
            return ResponseEntity.badRequest().body(Map.of("error", "Missing refresh_token"));
        }

        try {
            AuthResponse authResponse = authService.refreshToken(request.getRefreshToken());
            log.info("{} ✅ Token refreshed successfully for userId={}", CLASS, authResponse.getUserId());
            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            log.error("{} ❌ Token refresh failed → {}", CLASS, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired refresh token"));
        }
    }

    // ==========================================================
    // 🔹 Get current user profile (/me)
    // ==========================================================
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("{} ▶ Profile info request", CLASS);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("{} ⚠️ Missing or invalid Authorization header", CLASS);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid token"));
        }

        String token = authHeader.substring(7);

        try {
            String username = jwtUtil.extractUsername(token);
            String email = jwtUtil.extractEmail(token);
            String mobile = jwtUtil.extractMobile(token);

            log.debug("{} 🔍 Extracted from token → username={}, email={}, mobile={}", CLASS, username, email, mobile);

            // Try both mobile and email
            Optional<User> userOpt = userRepo.findByMobileNumber(mobile);
            if (userOpt.isEmpty() && email != null) {
                userOpt = userRepo.findByEmail(email);
            }

            return userOpt
                    .map(u -> {
                        log.info("{} ✅ Returning profile for userId={}", CLASS, u.getId());
                        return ResponseEntity.ok(Map.of(
                                "id", u.getId(),
                                "mobileNumber", u.getMobileNumber(),
                                "email", u.getEmail(),
                                "firstName", u.getFirstName(),
                                "lastName", u.getLastName()
                        ));
                    })
                    .orElseGet(() -> {
                        log.warn("{} ❌ No user found for token principal={}", CLASS, username);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", "User not found"));
                    });

        } catch (Exception e) {
            log.error("{} ❌ Invalid JWT → {}", CLASS, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
        }
    }
}
