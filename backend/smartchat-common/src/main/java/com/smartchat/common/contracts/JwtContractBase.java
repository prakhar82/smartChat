/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.contracts;

/**
 * Shared logical JWT operations (no Feign or Spring).
 * Implemented locally in Auth, or called via Feign elsewhere.
 */
public interface JwtContractBase {
    boolean validateToken(String token);

    String extractUsername(String token);

    Long extractUserId(String token);

    boolean isTokenExpired(String token);

    Object getAllClaims(String token);

    String extractMobileNumber(String token);

    Boolean isTokenValid(String token, String username);

    default boolean isValid(String token) {
        return !isTokenExpired(token) && validateToken(token);
    }

}
