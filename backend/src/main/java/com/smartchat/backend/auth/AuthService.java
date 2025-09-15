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
import com.smartchat.backend.service.PhoneNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNumberService phoneNumberService;
    private final InviteTokenRepository inviteTokenRepository;
    private final InviteTokenService inviteTokenService;

    /**
     * Handles user login by verifying mobile number and password.
     */

    public AuthResponse login(String mobileNumber, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(mobileNumber, password)
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(mobileNumber);
        User user = userRepository.findByMobileNumber(mobileNumber).orElseThrow();

        String accessToken = jwtUtil.generateAccessToken(user.getId(), userDetails.getUsername(), List.of(user.getRole()));
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), userDetails.getUsername());

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

    /*public AuthResponse login(String mobileNumber, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(mobileNumber, password)
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(mobileNumber);
        User user = userRepository.findByMobileNumber(mobileNumber).orElseThrow();

        // ✅ Pass userId explicitly
        String accessToken = jwtUtil.generateAccessToken(user.getId(), userDetails.getUsername(), List.of(user.getRole()));
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), userDetails.getUsername());

        return new AuthResponse(accessToken, refreshToken, user.getId());
    }
*/

    /**
     * Refresh JWT access token using a valid refresh token.
     */

    /**
     * Refresh JWT access token using a valid refresh token.
     */
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken, jwtUtil.extractUsername(refreshToken))) {
            throw new RuntimeException("Invalid refresh token");
        }

        String username = jwtUtil.extractUsername(refreshToken);
        User user = userRepository.findByMobileNumber(username).orElseThrow();

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId(),
                username,
                List.of(user.getRole())
        );

        return new AuthResponse(newAccessToken, refreshToken, user.getId());
    }


    /*public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken, jwtUtil.extractUsername(refreshToken))) {
            throw new RuntimeException("Invalid refresh token");
        }

        String username = jwtUtil.extractUsername(refreshToken);
        User user = userRepository.findByMobileNumber(username).orElseThrow();

        // ✅ Pass userId explicitly
        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), username, List.of(user.getRole()));

        return new AuthResponse(newAccessToken, refreshToken, user.getId());
    }
*/

    /**
     * Register a new user into the system.
     */

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByMobileNumber(request.getMobileNumber()).isPresent()) {
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

        String normalized = phoneNumberService.normalizeToE164(request.getMobileNumber(), request.getCountryCode());
        newUser.setMobileNormalized(normalized);

        // Referral handling...
        if (request.getReferralToken() != null && !request.getReferralToken().isBlank()) {
            InviteToken inviteToken = inviteTokenRepository.findByToken(request.getReferralToken())
                    .orElseThrow(() -> new RuntimeException("Invalid referral token"));
            if (inviteToken.isUsed()) throw new RuntimeException("Referral token already used");

            inviteToken.markUsed();
            inviteTokenRepository.save(inviteToken);

            User inviter = inviteToken.getInviter();
            userRepository.save(inviter);

            newUser.setReferredBy(inviter);
        }

        userRepository.save(newUser);

        String accessToken = jwtUtil.generateAccessToken(newUser.getId(), newUser.getMobileNumber(), List.of(newUser.getRole()));
        String refreshToken = jwtUtil.generateRefreshToken(newUser.getId(), newUser.getMobileNumber());

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

    /*public AuthResponse register(RegisterRequest request) {
        // 1. Validate duplicate mobile
        if (userRepository.findByMobileNumber(request.getMobileNumber()).isPresent()) {
            throw new RuntimeException("Mobile number already registered");
        }

        // 2. Create User
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

        // 3. Normalize phone number
        String normalized = phoneNumberService.normalizeToE164(
                request.getMobileNumber(),
                request.getCountryCode()
        );
        newUser.setMobileNormalized(normalized);

        // 4. Handle referral token if provided
        if (request.getReferralToken() != null && !request.getReferralToken().isBlank()) {
            InviteToken inviteToken = inviteTokenRepository.findByToken(request.getReferralToken())
                    .orElseThrow(() -> new RuntimeException("Invalid referral token"));

            if (inviteToken.isUsed()) {
                throw new RuntimeException("Referral token already used");
            }

            // ✅ Mark as used
            inviteToken.markUsed();
            inviteTokenRepository.save(inviteToken);

            // (Optional) reward inviter
            User inviter = inviteToken.getInviter();
            // e.g. inviter.addBonusCredits(50);
            userRepository.save(inviter);

            // (Optional) link inviter to new user
            newUser.setReferredBy(inviter);
        }

        // 5. Save user
        userRepository.save(newUser);

        // 6. Generate tokens (✅ with userId)
        String accessToken = jwtUtil.generateAccessToken(
                newUser.getId(),
                newUser.getMobileNumber(),
                List.of(newUser.getRole())
        );
        String refreshToken = jwtUtil.generateRefreshToken(newUser.getId(), newUser.getMobileNumber());

        return new AuthResponse(
                accessToken,
                refreshToken,
                newUser.getId(),
                newUser.getFirstName(),
                "Registration successful"
        );
    }
*/

}
