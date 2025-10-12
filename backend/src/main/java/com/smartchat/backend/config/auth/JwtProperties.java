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
import org.springframework.context.annotation.Configuration;

/**
 * ==========================================================
 * ✅ JwtProperties
 * ----------------------------------------------------------
 * Centralized configuration for JWT and authentication settings.
 * Mapped from application.yml under prefix: {@code security.jwt}
 * <p>
 * Example:
 * security:
 * jwt:
 * secret: "base64-encoded-512-bit-secret"
 * expiration: 900000              # 15 minutes (access token)
 * refresh-expiration: 604800000   # 7 days
 * reset-token-expiration: 900000  # 15 minutes (for password resets)
 * verification-token-expiration: 86400000 # 24 hours (email verification)
 * max-login-attempts: 5
 * lockout-duration: 300000        # 5 minutes
 * password:
 * strength: 12
 * ==========================================================
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /**
     * 🔑 Base64 or plain secret key for HMAC SHA-256 signing.
     */
    private String secret;

    /**
     * ⏱ Access token validity in milliseconds (default ~15 min).
     */
    private long expiration = 900_000;

    /**
     * 🔁 Refresh token validity in milliseconds (default ~7 days).
     */
    private long refreshExpiration = 604_800_000;

    /**
     * 🧾 Password reset token validity in milliseconds.
     */
    private long resetTokenExpiration = 900_000;

    /**
     * 📧 Email verification token validity in milliseconds.
     */
    private long verificationTokenExpiration = 86_400_000;

    /**
     * 🧠 Max allowed login attempts before temporary lockout.
     */
    private int maxLoginAttempts = 5;

    /**
     * 🚫 Lockout duration in milliseconds after max failed attempts.
     */
    private long lockoutDuration = 300_000;

    /**
     * 🔐 Password configuration section.
     */
    private Password password = new Password();

    @Data
    public static class Password {
        /**
         * BCrypt password hashing strength (default: 12).
         */
        private int strength = 12;
    }
}
