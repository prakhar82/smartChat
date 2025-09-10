/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.config.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private String secret;                  // JWT secret key
    private long expiration;                // Access token expiration
    private long refreshExpiration;         // Refresh token expiration
    private long resetTokenExpiration;      // Reset token expiration
    private long verificationTokenExpiration; // Email verification expiration
    private int maxLoginAttempts;           // Brute-force protection
    private long lockoutDuration;           // Lockout duration (ms)

    private Password password = new Password();

    @Data
    public static class Password {
        private int strength; // BCrypt strength
    }
}