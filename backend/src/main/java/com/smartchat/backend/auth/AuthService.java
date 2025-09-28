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
import com.smartchat.backend.repository.InviteTokenRepository;
import com.smartchat.backend.repository.UserRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InviteTokenRepository inviteTokenRepository;
    private final InviteTokenService inviteTokenService;

    /**
     * Handles user login by verifying mobile number and password.
     */
    public AuthResponse login(String mobileNumber, String password) {
        log.info("[AuthService] Attempting login for mobileNumber={}", mobileNumber);

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(mobileNumber, password)
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(mobileNumber);
        User user = userRepository.findByMobileNumber(mobileNumber).orElseThrow();

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(),
                userDetails.getUsername(),
                List.of(user.getRole())
        );
        String refreshToken = jwtUtil.generateRefreshToken(
                user.getId(),
                userDetails.getUsername()
        );

        log.info("[AuthService] Login successful for userId={}", user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .message("Login successful")
                .build();
    }

    /**
     * Refresh JWT access token using a valid refresh token.
     */
    public AuthResponse refreshToken(String refreshToken) {
        log.debug("[AuthService] Refreshing access token using refreshToken");

        if (!jwtUtil.isTokenValid(refreshToken, jwtUtil.extractUsername(refreshToken))) {
            log.error("[AuthService] Invalid refresh token");
            throw new RuntimeException("Invalid refresh token");
        }

        String username = jwtUtil.extractUsername(refreshToken);
        User user = userRepository.findByMobileNumber(username).orElseThrow();

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId(),
                username,
                List.of(user.getRole())
        );

        log.info("[AuthService] Issued new access token for userId={}", user.getId());

        return new AuthResponse(newAccessToken, refreshToken, user.getId());
    }

    /**
     * Register a new user into the system.
     */
    public AuthResponse register(RegisterRequest request) {
        log.info("[AuthService] Registering new user mobileNumber={}", request.getMobileNumber());

        if (userRepository.findByMobileNumber(request.getMobileNumber()).isPresent()) {
            log.warn("[AuthService] Mobile number already registered: {}", request.getMobileNumber());
            throw new RuntimeException("Mobile number already registered");
        }

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

        // ✅ Simple normalization
        String normalized = normalizePhone(request.getCountryCode(), request.getMobileNumber());
        newUser.setMobileNormalized(normalized);

        // Handle referral token
        if (request.getReferralToken() != null && !request.getReferralToken().isBlank()) {
            log.debug("[AuthService] Processing referral token {}", request.getReferralToken());

            InviteToken inviteToken = inviteTokenRepository.findByToken(request.getReferralToken())
                    .orElseThrow(() -> new RuntimeException("Invalid referral token"));
            if (inviteToken.isUsed()) {
                log.error("[AuthService] Referral token already used: {}", request.getReferralToken());
                throw new RuntimeException("Referral token already used");
            }

            inviteToken.markUsed();
            inviteTokenRepository.save(inviteToken);

            User inviter = inviteToken.getInviter();
            userRepository.save(inviter);

            newUser.setReferredBy(inviter);
            log.info("[AuthService] User {} referred by {}", newUser.getMobileNumber(), inviter.getMobileNumber());
        }

        userRepository.save(newUser);

        String accessToken = jwtUtil.generateAccessToken(
                newUser.getId(),
                newUser.getMobileNumber(),
                List.of(newUser.getRole())
        );
        String refreshToken = jwtUtil.generateRefreshToken(
                newUser.getId(),
                newUser.getMobileNumber()
        );

        log.info("[AuthService] Registration successful for userId={}", newUser.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(newUser.getId())
                .firstName(newUser.getFirstName())
                .lastName(newUser.getLastName())
                .email(newUser.getEmail())
                .message("Registration successful")
                .build();
    }

    /**
     * Simple phone normalization without libphonenumber.
     */
    private String normalizePhone(String countryCode, String rawMobile) {
        if (rawMobile == null) return null;
        String cleaned = rawMobile.replaceAll("[\\s\\-()]", "");
        if (cleaned.startsWith("+")) {
            return cleaned;
        }
        if (countryCode != null && !countryCode.isBlank()) {
            return countryCode + cleaned.replaceFirst("^0+", "");
        }
        return cleaned;
    }
}
