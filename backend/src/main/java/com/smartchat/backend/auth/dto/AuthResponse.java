/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.auth.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AuthResponse is returned after login/register.
 * Contains tokens, userId, and optionally a message for errors.
 */

@Data
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private String message;

    public AuthResponse(String accessToken, String refreshToken, Long userId, String message) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.message = message;
    }

    public AuthResponse(String accessToken, String refreshToken, Long userId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
    }

    public AuthResponse(String accessToken, String refreshToken, Long id, String firstName, String registrationSuccessful) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.firstName = firstName;
        this.message = message;
    }
}
