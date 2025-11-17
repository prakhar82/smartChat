/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.auth.contracts;

import com.smartchat.auth.model.InviteToken;
import com.smartchat.auth.model.User;
import com.smartchat.auth.repository.InviteTokenRepository;
import com.smartchat.auth.repository.UserRepository;
import com.smartchat.common.contracts.AuthContract;
import com.smartchat.common.dto.InviteTokenDTO;
import com.smartchat.common.dto.UserDTO;
import com.smartchat.common.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * [AuthContractImpl]
 * ------------------------------------------------------------
 * Bridges the Auth module with other SmartChat services via DTOs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthContractImpl implements AuthContract {

    private static final String CLASS = "[AuthContractImpl]";

    private final UserRepository userRepository;
    private final InviteTokenRepository inviteTokenRepository;
    private final JwtUtil jwtUtil;


    // ==========================================================
    // 🧩 Get user by ID
    // ==========================================================
    @Override
    public UserDTO getUserById(String userId) {
        log.debug("{} ▶ Fetching user by ID={}", CLASS, userId);
        try {
            Long id = Long.parseLong(userId);
            return userRepository.findById(id)
                    .map(this::mapToUserDTO)
                    .orElse(null);
        } catch (Exception e) {
            log.error("{} ❌ Failed to fetch user by ID={} → {}", CLASS, userId, e.getMessage(), e);
            return null;
        }
    }

    // ==========================================================
    // 🧠 Get user by JWT token
    // ==========================================================
    @Override
    public UserDTO getUserByToken(String jwtToken) {
        log.debug("{} ▶ Resolving user from JWT token", CLASS);
        try {
            Long userId = jwtUtil.extractUserId(jwtToken);
            return userId != null ? getUserById(String.valueOf(userId)) : null;
        } catch (Exception e) {
            log.error("{} ❌ Failed to parse JWT → {}", CLASS, e.getMessage(), e);
            return null;
        }
    }

    // ==========================================================
    // 📧 Get user by Email
    // ==========================================================
    @Override
    public UserDTO getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(this::mapToUserDTO)
                .orElse(null);
    }

    // ==========================================================
    // 📱 Get user by Mobile
    // ==========================================================
    @Override
    public UserDTO getUserByMobile(String mobile) {
        return userRepository.findByMobileNumber(mobile)
                .map(this::mapToUserDTO)
                .orElse(null);
    }

    // ==========================================================
    // 🎟️ Validate invite token
    // ==========================================================
    @Override
    public InviteTokenDTO validateInviteToken(String token) {
        log.debug("{} ▶ Validating invite token={}", CLASS, token);
        try {
            InviteToken inviteToken = inviteTokenRepository.findByToken(token).orElse(null);
            if (inviteToken == null || inviteToken.isUsed()) return null;

            Instant expiresAt = inviteToken.getCreatedAt().plusSeconds(86400); // 24h TTL
            return InviteTokenDTO.builder()
                    .token(inviteToken.getToken())
                    .invitedEmail(inviteToken.getInviter() != null ? inviteToken.getInviter().getEmail() : null)
                    .expiresAt(expiresAt.toEpochMilli())
                    .build();
        } catch (Exception e) {
            log.error("{} ❌ Exception while validating token={} → {}", CLASS, token, e.getMessage(), e);
            return null;
        }
    }

    // ==========================================================
    // 👥 Get all users
    // ==========================================================
    @Override
    public List<UserDTO> getAllUsers() {
        log.debug("{} ▶ Fetching all registered users", CLASS);
        try {
            return userRepository.findAll().stream()
                    .map(this::mapToUserDTO)
                    .toList();
        } catch (Exception e) {
            log.error("{} ❌ Failed to fetch all users → {}", CLASS, e.getMessage(), e);
            return List.of();
        }
    }

    // ==========================================================
    // 🔍 Resolve principal (email / mobile / ID)
    // ==========================================================
    @Override
    public Long resolveUserIdFromPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            log.warn("{} ⚠️ Principal is null or blank", CLASS);
            return null;
        }

        // Try email
        var emailUser = userRepository.findByEmail(principal);
        if (emailUser.isPresent()) return emailUser.get().getId();

        // Try mobile
        var mobileUser = userRepository.findByMobileNumber(principal);
        if (mobileUser.isPresent()) return mobileUser.get().getId();

        // Try numeric
        try {
            Long id = Long.parseLong(principal);
            if (userRepository.existsById(id)) return id;
        } catch (NumberFormatException ignored) {
        }

        log.warn("{} ⚠️ No user found for principal={}", CLASS, principal);
        return null;
    }

    // ==========================================================
    // 🧭 Mapper — User → UserDTO
    // ==========================================================
    private UserDTO mapToUserDTO(User user) {
        if (user == null) return null;

        String fullName = (user.getFirstName() != null ? user.getFirstName() : "")
                + (user.getLastName() != null ? " " + user.getLastName() : "");

        return UserDTO.builder()
                .id(String.valueOf(user.getId()))
                .email(user.getEmail())
                .name(fullName.trim())
                .phoneNumber(user.getMobileNumber())
                .build();
    }


}
