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
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.PhoneNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNumberService phoneNumberService;

    /**
     * Handles user login by verifying mobile number and password.
     */
    public AuthResponse login(String mobileNumber, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(mobileNumber, password)
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(mobileNumber);
        User user = userRepository.findByMobileNumber(mobileNumber).orElseThrow();

        String accessToken = jwtUtil.generateAccessToken(userDetails.getUsername(), List.of(user.getRole()));
        String refreshToken = jwtUtil.generateRefreshToken(userDetails.getUsername());

        return new AuthResponse(accessToken, refreshToken, user.getId());
    }

    /**
     * Refresh JWT access token using a valid refresh token.
     */
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken, jwtUtil.extractUsername(refreshToken))) {
            throw new RuntimeException("Invalid refresh token");
        }

        String username = jwtUtil.extractUsername(refreshToken);
        User user = userRepository.findByMobileNumber(username).orElseThrow();

        String newAccessToken = jwtUtil.generateAccessToken(username, List.of(user.getRole()));

        return new AuthResponse(newAccessToken, refreshToken, user.getId());
    }

    /**
     * Register a new user into the system.
     */
    public AuthResponse register(RegisterRequest request) {
        // 1. Validate
        if (userRepository.findByMobileNumber(request.getMobileNumber()).isPresent()) {
            throw new RuntimeException("Mobile number already registered");
        }

        // 2. Create User
        User newUser = new User();
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setCountryCode(request.getCountryCode());
        newUser.setMobileNumber(request.getMobileNumber());
        newUser.setMobileNormalized(
                request.getCountryCode() + request.getMobileNumber()
        );
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

        // 4. Save
        userRepository.save(newUser);

        // 5. Generate tokens
        String accessToken = jwtUtil.generateAccessToken(newUser.getMobileNumber(), List.of(newUser.getRole()));
        String refreshToken = jwtUtil.generateRefreshToken(newUser.getMobileNumber());

        return new AuthResponse(accessToken, refreshToken, newUser.getId(), newUser.getFirstName(), "Registration successful");
    }

}
