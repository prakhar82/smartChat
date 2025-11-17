/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.auth.controller;

import com.smartchat.auth.dto.AuthRequest;
import com.smartchat.auth.dto.AuthResponse;
import com.smartchat.auth.dto.RefreshTokenRequest;
import com.smartchat.auth.dto.RegisterRequest;
import com.smartchat.auth.service.AuthService;
import com.smartchat.common.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ==========================================================
    // 📝 Register new user
    // ==========================================================
    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        log.info("[AuthController] ▶ Registering user: {}", request.getEmail());
        return ResponseEntity.ok(authService.register(request));
    }

    // ==========================================================
    // 🔐 User login
    // ==========================================================
    @PostMapping(value = "/login", produces = "application/json")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        log.info("[AuthController] ▶ Login attempt for: {}", request.getMobileNumber());
        AuthResponse res = authService.login(request.getMobileNumber(), request.getPassword());
        log.info("[AuthController] ✅ Returning AuthResponse: {}", res);
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(res);
    }


    // ==========================================================
    // 🔁 Refresh token
    // ==========================================================
    @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        log.info("[AuthController] ▶ Refreshing JWT token");
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    // ==========================================================
    // ✉️ Verify email token (optional)
    // ==========================================================
    @GetMapping(value = "/verify", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token) {
        log.info("[AuthController] ▶ Verifying email token");
        authService.verifyToken(token);
        return ResponseEntity.ok("Email verified successfully!");
    }

    // ==========================================================
    // 🔍 Internal / Public Token Validation
    // ==========================================================
    @GetMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validateToken(
            @RequestParam(value = "jwtToken", required = false) String jwtToken,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        log.info("[AuthController] ▶ Received /auth/token validation request");

        try {
            // 🔹 Accept either query param OR Bearer header
            String token = jwtToken;
            if ((token == null || token.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            if (token == null || token.isBlank()) {
                log.warn("[AuthController] ⚠️ Missing JWT token in request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Missing jwtToken or Authorization header");
            }

            // 🔐 Delegate validation and user mapping
            UserDTO user = authService.getCurrentUser("Bearer " + token);
            if (user == null) {
                log.warn("[AuthController] ⚠️ Invalid or expired token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid or expired token");
            }

            log.info("[AuthController] ✅ Token validated successfully for userId={}", user.getId());
            return ResponseEntity.ok(user);

        } catch (Exception e) {
            log.error("[AuthController] ❌ Token validation failed → {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Forbidden: " + e.getMessage());
        }
    }


    // ==========================================================
    // 👤 Fetch current user info (optional, JWT-secured)
    // ==========================================================
    @GetMapping(value = {"/me", "/profile"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDTO> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        log.info("[AuthController] ▶ Fetching current user info");
        return ResponseEntity.ok(authService.getCurrentUser(authHeader));
    }

    @GetMapping("/resolve")
    public ResponseEntity<Long> resolvePrincipal(@RequestParam("principal") String principal) {
        Long userId = authService.resolvePrincipal(principal);
        return ResponseEntity.ok(userId);
    }


}
