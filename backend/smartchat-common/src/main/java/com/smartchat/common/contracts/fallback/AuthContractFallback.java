/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.contracts.fallback;

import com.smartchat.common.contracts.AuthContract;
import com.smartchat.common.dto.InviteTokenDTO;
import com.smartchat.common.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ==========================================================
 * 🧩 AuthContractFallback (Resilience4j Safe Fallback)
 * ----------------------------------------------------------
 * Used when the Auth Service is unavailable, unresponsive,
 * or the circuit breaker opens.
 * <p>
 * Provides safe, default responses so other services
 * (Contact, Chat, API Gateway) can degrade gracefully.
 * ==========================================================
 */
@Slf4j
@Component
public class AuthContractFallback implements AuthContract {

    private static final String CLASS = "[AuthContractFallback]";

    /**
     * Internal helper to create stubbed placeholder user.
     */
    private UserDTO stubUser(String userId, String note) {
        return UserDTO.builder()
                .id(userId != null ? userId : "0")
                .name("Unavailable User")
                .email("unavailable@auth.local")
                .phoneNumber("unavailable")
                .build();
    }

    @Override
    public UserDTO getUserById(String userId) {
        log.warn("{} ⚠️ Auth service unreachable — returning stub for userId={}", CLASS, userId);
        return stubUser(userId, "fallback:getUserById");
    }

    @Override
    public UserDTO getUserByToken(String jwtToken) {
        log.warn("{} ⚠️ Auth service unavailable — token validation skipped", CLASS);
        // Returning null signals "unauthenticated" to caller (ContactService can handle gracefully)
        return null;
    }

    @Override
    public InviteTokenDTO validateInviteToken(String token) {
        log.warn("{} ⚠️ Auth service unavailable — marking invite token as invalid", CLASS);
        return InviteTokenDTO.builder()
                .token(token != null ? token : "unknown")
                .invitedEmail("unknown@fallback.local")
                .expiresAt(Instant.now().toEpochMilli())
                .build();
    }

    @Override
    public List<UserDTO> getAllUsers() {
        log.warn("{} ⚠️ Auth service unavailable — returning empty user list", CLASS);
        return Collections.emptyList();
    }

    @Override
    public UserDTO getUserByEmail(String email) {
        log.warn("{} ⚠️ Auth service unavailable — cannot fetch user by email={}", CLASS, email);
        return stubUser(UUID.randomUUID().toString(), "fallback:getUserByEmail");
    }

    @Override
    public UserDTO getUserByMobile(String mobile) {
        log.warn("{} ⚠️ Auth service unavailable — cannot fetch user by mobile={}", CLASS, mobile);
        return stubUser(UUID.randomUUID().toString(), "fallback:getUserByMobile");
    }

    @Override
    public Long resolveUserIdFromPrincipal(String principal) {
        log.warn("{} ⚠️ Auth service unavailable — cannot resolve principal={}", CLASS, principal);
        return 0L;
    }
}
