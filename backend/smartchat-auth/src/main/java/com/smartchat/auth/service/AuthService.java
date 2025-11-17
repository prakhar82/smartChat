/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.auth.service;

import com.smartchat.auth.dto.AuthResponse;
import com.smartchat.auth.dto.RegisterRequest;
import com.smartchat.auth.model.InviteToken;
import com.smartchat.auth.model.User;
import com.smartchat.auth.repository.InviteTokenRepository;
import com.smartchat.auth.repository.UserRepository;
import com.smartchat.auth.service.impl.CustomUserDetailsService;
import com.smartchat.common.security.jwt.JwtUtil;
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
 * ============================================================================
 * 🧠 AuthService (SmartChat Authentication Core)
 * ----------------------------------------------------------------------------
 * Handles:
 * ✔ Login (JWT access + refresh)
 * ✔ Registration (with referral support)
 * ✔ Token refresh
 * ✔ Current user lookup from JWT
 * ✔ Email verification
 * ✔ Principal resolution (mobile/email)
 * <p>
 * SECURITY NOTES:
 * - All tokens generated using JwtUtil
 * - Access token short-lived
 * - Refresh token long-lived
 * - Stateless (no sessions)
 * ============================================================================
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

    // =============================================================================
    //  LOGIN — Authenticate and generate access + refresh tokens
    // =============================================================================

    /**
     * Authenticates a user and issues new JWT tokens.
     *
     * @param mobileNumber registered mobile number
     * @param password     raw password
     * @return AuthResponse containing JWT tokens & user info
     */
    public AuthResponse login(String mobileNumber, String password) {
        log.info("{} ▶ Login attempt for {}", CLASS, mobileNumber);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(mobileNumber, password)
            );
        } catch (Exception e) {
            log.warn("{} ❌ Invalid credentials for {} — {}", CLASS, mobileNumber, e.getMessage());
            throw new RuntimeException("Invalid credentials");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(mobileNumber);
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> roles = normalizeRoles(user);

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

        log.info("{} ✅ Login successful → userId={}", CLASS, user.getId());

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

    // =============================================================================
    //  REFRESH TOKEN — Issue new access token from refresh token
    // =============================================================================

    /**
     * Validates refresh token and issues a new access token.
     *
     * @param refreshToken JWT refresh token
     * @return new access token inside AuthResponse
     */
    public AuthResponse refreshToken(String refreshToken) {
        log.info("{} ▶ Refreshing access token", CLASS);

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("Missing refresh token");
        }

        String username;
        try {
            username = jwtUtil.extractUsername(refreshToken);
        } catch (Exception e) {
            log.error("{} ❌ Failed to parse refresh token — {}", CLASS, e.getMessage());
            throw new RuntimeException("Invalid refresh token");
        }

        if (!jwtUtil.isTokenValid(refreshToken)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        User user = userRepository.findByMobileNumber(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> roles = normalizeRoles(user);

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId(),
                username,
                user.getEmail(),
                user.getMobileNumber(),
                roles
        );

        log.info("{} 🔄 Access token refreshed for userId={}", CLASS, user.getId());

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

    // =============================================================================
    //  REGISTER — Create new user + referral support
    // =============================================================================

    /**
     * Registers a new SmartChat user.
     *
     * @param request registration payload
     * @return created user's tokens and info
     */
    public AuthResponse register(RegisterRequest request) {
        log.info("{} ▶ Registration start — mobile={}", CLASS, request.getMobileNumber());

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        userRepository.findByMobileNumber(request.getMobileNumber()).ifPresent(u -> {
            throw new RuntimeException("Mobile number already registered");
        });

        User newUser = new User();
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setCountryCode(request.getCountryCode());
        newUser.setMobileNumber(request.getMobileNumber());
        newUser.setEmail(request.getEmail());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setRole("ROLE_USER");
        newUser.setEmailVerified(false);
        newUser.setCreatedAt(Instant.now());
        newUser.setUpdatedAt(Instant.now());
        newUser.setMobileNormalized(normalizePhone(request.getCountryCode(), request.getMobileNumber()));

        handleReferral(request, newUser);
        userRepository.save(newUser);

        List<String> roles = normalizeRoles(newUser);

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

        log.info("{} ✅ Registration complete — userId={}", CLASS, newUser.getId());

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

    // =============================================================================
    //  CURRENT USER — Resolve from JWT
    // =============================================================================

    /**
     * Retrieves current user from Authorization header.
     *
     * @param authHeader "Bearer <token>"
     * @return UserDTO representation
     */
    public com.smartchat.common.dto.UserDTO getCurrentUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            Long userId = jwtUtil.extractUserId(token);
            if (userId == null) {
                throw new RuntimeException("Invalid JWT token");
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String fullName =
                    (user.getFirstName() != null ? user.getFirstName() : "") +
                            (user.getLastName() != null ? " " + user.getLastName() : "");

            return com.smartchat.common.dto.UserDTO.builder()
                    .id(String.valueOf(user.getId()))
                    .email(user.getEmail())
                    .name(fullName.trim())
                    .phoneNumber(user.getMobileNumber())
                    .build();

        } catch (Exception e) {
            log.error("{} ❌ Failed to fetch current user — {}", CLASS, e.getMessage());
            throw new RuntimeException("Invalid or expired token");
        }
    }

    // =============================================================================
    //  EMAIL VERIFICATION
    // =============================================================================

    /**
     * Verify email token and mark user as verified.
     *
     * @param token email verification token
     */
    public void verifyToken(String token) {
        log.info("{} ▶ Email verification flow started", CLASS);

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verification token is missing");
        }

        try {
            Long userId = jwtUtil.extractUserId(token);
            if (userId == null) {
                throw new RuntimeException("Invalid or expired verification token");
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (user.isEmailVerified()) {
                log.info("{} ✔ User already verified — userId={}", CLASS, user.getId());
                return;
            }

            user.setEmailVerified(true);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            log.info("{} ✅ Email verified — userId={}", CLASS, user.getId());

        } catch (Exception e) {
            log.error("{} ❌ Email token verification failed — {}", CLASS, e.getMessage());
            throw new RuntimeException("Invalid or expired verification token");
        }
    }

    // =============================================================================
    //  HELPER — REFERRAL HANDLING
    // =============================================================================

    private void handleReferral(RegisterRequest request, User newUser) {
        if (request.getReferralToken() == null || request.getReferralToken().isBlank()) return;

        InviteToken inviteToken = inviteTokenRepository
                .findByToken(request.getReferralToken())
                .orElseThrow(() -> new RuntimeException("Invalid referral token"));

        if (inviteToken.isUsed()) {
            throw new RuntimeException("Referral token already used");
        }

        inviteToken.markUsed();
        inviteTokenRepository.save(inviteToken);

        newUser.setReferredBy(inviteToken.getInviter());
    }

    // =============================================================================
    //  HELPER — NORMALIZE ROLES
    // =============================================================================

    private List<String> normalizeRoles(User user) {
        String role = user.getRole();
        return List.of(role.startsWith("ROLE_") ? role : "ROLE_" + role);
    }

    // =============================================================================
    //  HELPER — NORMALIZE PHONE NUMBER
    // =============================================================================

    private String normalizePhone(String countryCode, String rawMobile) {
        if (rawMobile == null) return null;

        String cleaned = rawMobile.replaceAll("[\\s\\-()]", "");

        if (cleaned.startsWith("+")) return cleaned;

        if (countryCode != null && !countryCode.isBlank()) {
            return countryCode + cleaned.replaceFirst("^0+", "");
        }

        return cleaned;
    }

    // =============================================================================
    //  PRINCIPAL RESOLUTION (mobile OR email)
    // =============================================================================

    /**
     * Resolves principal (mobile or email) to userId.
     * Used by Contact Service & Gateway for OAuth flows.
     *
     * @param principal mobile or email
     * @return userId or null if not found
     */
    public Long resolvePrincipal(String principal) {
        log.debug("{} ▶ Resolving principal={}", CLASS, principal);

        if (principal == null || principal.isBlank()) {
            log.warn("{} ⚠ Empty principal", CLASS);
            return null;
        }

        try {
            // Try mobile first
            User user = userRepository.findByMobileNumber(principal).orElse(null);

            // Fallback to email
            if (user == null) {
                user = userRepository.findByEmail(principal).orElse(null);
            }

            if (user != null) {
                log.info("{} ✔ Resolved principal={} → userId={}", CLASS, principal, user.getId());
                return user.getId();
            }

            log.warn("{} ⚠ Principal not found: {}", CLASS, principal);
            return null;

        } catch (Exception e) {
            log.error("{} ❌ Failed to resolve principal={} — {}", CLASS, principal, e.getMessage());
            return null;
        }
    }
}
