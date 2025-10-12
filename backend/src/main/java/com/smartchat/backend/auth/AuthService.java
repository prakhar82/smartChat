/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.auth;

import com.smartchat.backend.auth.dto.AuthResponse;
import com.smartchat.backend.auth.dto.RegisterRequest;
import com.smartchat.backend.auth.service.CustomUserDetailsService;
import com.smartchat.backend.model.InviteToken;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.jpa.InviteTokenRepository;
import com.smartchat.backend.repository.jpa.UserRepository;
import com.smartchat.backend.service.InviteTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * ==========================================================
 * ✅ AuthService
 * ----------------------------------------------------------
 * Core authentication service responsible for:
 * - User login
 * - Registration
 * - JWT token refresh
 * - Invite/referral handling
 * ==========================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CLASS = "[AuthService]";

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InviteTokenRepository inviteTokenRepository;
    private final InviteTokenService inviteTokenService;

    // ==========================================================
    // 🔹 LOGIN — Authenticate user and issue JWT tokens
    // ==========================================================
    public AuthResponse login(String mobileNumber, String password) {
        log.info("{} ▶ Attempting login for mobileNumber={}", CLASS, mobileNumber);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(mobileNumber, password)
            );
        } catch (Exception e) {
            log.warn("{} ❌ Authentication failed for mobileNumber={} → {}", CLASS, mobileNumber, e.getMessage());
            throw new RuntimeException("Invalid credentials");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(mobileNumber);
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> roles = List.of(user.getRole());

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(),
                userDetails.getUsername(),
                user.getEmail(),
                user.getMobileNumber(),
                roles
        );

        String refreshToken = jwtUtil.generateRefreshToken(
                user.getId(),
                userDetails.getUsername(),
                user.getEmail(),
                user.getMobileNumber()
        );

        log.info("{} ✅ Login successful → userId={} roles={}", CLASS, user.getId(), roles);

        return AuthResponse.builder()
                .authToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .message("Login successful")
                .build();
    }

    // ==========================================================
    // 🔹 REFRESH TOKEN — Issue new access token
    // ==========================================================
    public AuthResponse refreshToken(String refreshToken) {
        log.info("{} ▶ Refreshing access token", CLASS);

        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("{} ⚠️ Missing refresh token in request", CLASS);
            throw new RuntimeException("Missing refresh token");
        }

        String username;
        try {
            username = jwtUtil.extractUsername(refreshToken);
        } catch (Exception e) {
            log.error("{} ❌ Failed to extract username from refresh token → {}", CLASS, e.getMessage());
            throw new RuntimeException("Invalid refresh token");
        }

        if (!jwtUtil.isTokenValid(refreshToken, username)) {
            log.error("{} ❌ Invalid or expired refresh token for username={}", CLASS, username);
            throw new RuntimeException("Invalid or expired refresh token");
        }

        User user = userRepository.findByMobileNumber(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> roles = List.of(user.getRole());

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId(),
                username,
                user.getEmail(),
                user.getMobileNumber(),
                roles
        );

        log.info("{} ✅ Issued new access token for userId={}", CLASS, user.getId());

        return AuthResponse.builder()
                .authToken(newAccessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .message("Token refreshed successfully")
                .build();
    }

    // ==========================================================
    // 🔹 REGISTER — Create a new SmartChat user
    // ==========================================================
    public AuthResponse register(RegisterRequest request) {
        log.info("{} ▶ Registering new user → mobile={}", CLASS, request.getMobileNumber());

        // Prevent duplicate registration
        userRepository.findByMobileNumber(request.getMobileNumber()).ifPresent(u -> {
            log.warn("{} ❌ Mobile number already registered: {}", CLASS, request.getMobileNumber());
            throw new RuntimeException("Mobile number already registered");
        });

        // Create and populate user
        User newUser = new User();
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setCountryCode(request.getCountryCode());
        newUser.setMobileNumber(request.getMobileNumber());
        newUser.setEmail(request.getEmail());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setRole("USER");
        newUser.setCreatedAt(Instant.now());
        newUser.setUpdatedAt(Instant.now());
        newUser.setMobileNormalized(normalizePhone(request.getCountryCode(), request.getMobileNumber()));

        // Handle referral/invite token if provided
        handleReferral(request, newUser);

        userRepository.save(newUser);

        List<String> roles = List.of(newUser.getRole());

        String accessToken = jwtUtil.generateAccessToken(
                newUser.getId(),
                newUser.getMobileNumber(),
                newUser.getEmail(),
                newUser.getMobileNumber(),
                roles
        );

        String refreshToken = jwtUtil.generateRefreshToken(
                newUser.getId(),
                newUser.getMobileNumber(),
                newUser.getEmail(),
                newUser.getMobileNumber()
        );

        log.info("{} ✅ Registration successful → userId={}", CLASS, newUser.getId());

        return AuthResponse.builder()
                .authToken(accessToken)
                .refreshToken(refreshToken)
                .userId(newUser.getId())
                .firstName(newUser.getFirstName())
                .lastName(newUser.getLastName())
                .email(newUser.getEmail())
                .message("Registration successful")
                .build();
    }

    // ==========================================================
    // 🔹 Helper — Handle referral / invite logic
    // ==========================================================
    private void handleReferral(RegisterRequest request, User newUser) {
        if (request.getReferralToken() == null || request.getReferralToken().isBlank()) {
            return;
        }

        log.info("{} 🎟 Processing referral token {}", CLASS, request.getReferralToken());
        InviteToken inviteToken = inviteTokenRepository.findByToken(request.getReferralToken())
                .orElseThrow(() -> new RuntimeException("Invalid referral token"));

        if (inviteToken.isUsed()) {
            log.warn("{} ⚠️ Referral token already used → {}", CLASS, request.getReferralToken());
            throw new RuntimeException("Referral token already used");
        }

        inviteToken.markUsed();
        inviteTokenRepository.save(inviteToken);

        User inviter = inviteToken.getInviter();
        newUser.setReferredBy(inviter);

        log.info("{} 🤝 User {} referred by {}", CLASS, newUser.getMobileNumber(), inviter.getMobileNumber());
    }

    // ==========================================================
    // 🔹 Helper — Normalize phone numbers
    // ==========================================================
    private String normalizePhone(String countryCode, String rawMobile) {
        if (rawMobile == null) return null;
        String cleaned = rawMobile.replaceAll("[\\s\\-()]", "");
        if (cleaned.startsWith("+")) return cleaned;
        if (countryCode != null && !countryCode.isBlank()) {
            return countryCode + cleaned.replaceFirst("^0+", "");
        }
        return cleaned;
    }
}
