/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AuthResponse is returned after login/register.
 * Contains tokens, userId, and optionally a message for errors.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    @JsonProperty("auth_token")   // ✅ snake_case
    private String authToken;

    @JsonProperty("refresh_token") // ✅ snake_case
    private String refreshToken;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String email;
    private String message;

    // ============================
    // ✅ Optional constructors
    // ============================
    public AuthResponse(String authToken, String refreshToken, Long userId, String message) {
        this.authToken = authToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.message = message;
    }

    public AuthResponse(String authToken, String refreshToken, Long userId) {
        this.authToken = authToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
    }

    public AuthResponse(String authToken, String refreshToken, Long userId, String firstName, String message) {
        this.authToken = authToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.firstName = firstName;
        this.message = message;
    }
}
