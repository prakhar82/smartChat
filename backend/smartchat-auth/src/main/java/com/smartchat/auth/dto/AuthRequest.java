/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for login requests.
 * <p>
 * Purpose:
 * - This class represents the incoming JSON payload when a user attempts to log in.
 * - Instead of `username`, our system uses `mobileNumber` as the primary login identifier.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {

    /**
     * The mobile number of the user (acts as username).
     */
    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    /**
     * The raw password provided by the user.
     * <p>
     * IMPORTANT: This should never be stored in plain text.
     * It will be validated against the hashed password in the database.
     */
    @NotBlank(message = "Password is required")
    private String password;
}
