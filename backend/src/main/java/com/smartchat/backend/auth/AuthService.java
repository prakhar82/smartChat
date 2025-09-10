/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

package com.smartchat.backend.auth;

import com.smartchat.backend.auth.dto.AuthResponse;
import com.smartchat.backend.auth.dto.RegisterRequest;
import com.smartchat.backend.auth.service.CustomUserDetailsService;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Handles user login by verifying mobile number and password.
     * Why:
     *   - Validates user credentials and generates JWT tokens for secure access.
     * What:
     *   - Authenticate user.
     *   - Load user details and generate access & refresh tokens.
     *
     * @param mobileNumber user's mobile number
     * @param password     user's password
     * @return AuthResponse containing access and refresh tokens
     */
    public AuthResponse login(String mobileNumber, String password) {
        // 1. Authenticate the user credentials
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(mobileNumber, password)
        );

        // 2. Fetch user details
        UserDetails userDetails = userDetailsService.loadUserByUsername(mobileNumber);
        User user = userRepository.findByMobileNumber(mobileNumber).orElseThrow();

        // 3. Generate JWT tokens
        String accessToken = jwtUtil.generateAccessToken(userDetails.getUsername(), List.of(user.getRole()));
        String refreshToken = jwtUtil.generateRefreshToken(userDetails.getUsername());

        return new AuthResponse(accessToken, refreshToken);
    }

    /**
     * Refresh JWT access token using a valid refresh token.
     * Why:
     *   - Allow users to stay logged in without re-entering credentials.
     * What:
     *   - Validate the refresh token and issue a new access token.
     *
     * @param refreshToken the old refresh token
     * @return AuthResponse containing a new access token and the same refresh token
     */
    public AuthResponse refreshToken(String refreshToken) {
        // Validate the refresh token
        if (!jwtUtil.isTokenValid(refreshToken, jwtUtil.extractUsername(refreshToken))) {
            throw new RuntimeException("Invalid refresh token");
        }

        // Extract username from token and regenerate access token
        String username = jwtUtil.extractUsername(refreshToken);
        User user = userRepository.findByMobileNumber(username).orElseThrow();

        String newAccessToken = jwtUtil.generateAccessToken(username, List.of(user.getRole()));
        return new AuthResponse(newAccessToken, refreshToken);
    }

    /**
     * Register a new user into the system.
     * Why:
     *   - Create new user accounts securely.
     *   - Prevent duplicate mobile number registrations.
     * What:
     *   - Check if mobile number already exists.
     *   - Encode password for security.
     *   - Save the user to database and return JWT tokens.
     *
     * @param request RegisterRequest containing mobile number, password, role, etc.
     * @return AuthResponse with access and refresh tokens
     */
    public AuthResponse register(RegisterRequest request) {
        // 1. Check if the mobile number is already registered
        if (userRepository.findByMobileNumber(request.getMobileNumber()).isPresent()) {
            throw new RuntimeException("Mobile number already registered");
        }

        // 2. Create a new User entity and encode the password
        User newUser = new User();
        newUser.setMobileNumber(request.getMobileNumber());
        newUser.setPassword(passwordEncoder.encode(request.getPassword())); // Password hashing
        newUser.setRole(request.getRole() != null ? request.getRole() : "USER"); // Default role

        // 3. Save the new user to the database
        userRepository.save(newUser);

        // 4. Generate JWT tokens for the new user
        String accessToken = jwtUtil.generateAccessToken(newUser.getMobileNumber(), List.of(newUser.getRole()));
        String refreshToken = jwtUtil.generateRefreshToken(newUser.getMobileNumber());

        // 5. Return tokens as response
        return new AuthResponse(accessToken, refreshToken);
    }
}
