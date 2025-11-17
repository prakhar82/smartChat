/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.api.adapter;

import com.smartchat.common.contracts.AuthContract;
import com.smartchat.common.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * AuthAdapter
 * ----------------------------------------------------
 * Bridges API controllers with Auth microservice
 * through the Feign-based AuthContract.
 * Provides safe fallbacks and conversions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAdapter {

    private final AuthContract authContract;

    public Optional<UserDTO> getUserByToken(String token) {
        try {
            return Optional.ofNullable(authContract.getUserByToken(token));
        } catch (Exception e) {
            log.error("[AuthAdapter] ⚠️ Error contacting Auth service: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<UserDTO> getUserByEmail(String email) {
        try {
            return Optional.ofNullable(authContract.getUserByEmail(email));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<UserDTO> getUserByMobile(String mobile) {
        try {
            return Optional.ofNullable(authContract.getUserByMobile(mobile));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
