/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registration request sent by client.
 * - mobileNumber: primary identifier used in backend (matches User.mobileNumber)
 * - email: optional/validated email
 * - password: plain-text password (will be hashed server-side)
 * - role: optional; if provided and allowed by your business rules, will be used.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "mobileNumber is required")
    private String mobileNumber;

    @Email(message = "email must be a valid email address")
    private String email;

    @NotBlank(message = "password is required")
    private String password;

    private String name;

    /**
     * Optional: client may request a role. Prefer validating/ignoring this on server-side
     * unless you trust the client (most apps should default to ROLE_USER).
     */
    private String role;
}
