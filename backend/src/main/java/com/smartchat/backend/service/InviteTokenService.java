/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.smartchat.backend.model.InviteToken;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.InviteTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class InviteTokenService {

    private final InviteTokenRepository inviteTokenRepository;

    /**
     * Generate a new invite token for a user.
     */
    public InviteToken generateToken(User inviter) {
        String tokenValue = generateRandomToken();

        InviteToken token = InviteToken.builder()
                .token(tokenValue)
                .inviter(inviter)
                .used(false)
                .build();

        return inviteTokenRepository.save(token);
    }

    /**
     * Validate and mark token as used.
     */
    public InviteToken useToken(String tokenValue) {
        InviteToken token = inviteTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite token"));

        if (token.isUsed()) {
            throw new IllegalStateException("Invite token already used");
        }

        token.markUsed();
        return inviteTokenRepository.save(token);
    }

    /**
     * Check if token exists and is not yet used.
     */
    public boolean isValid(String tokenValue) {
        return inviteTokenRepository.existsByTokenAndUsedFalse(tokenValue);
    }

    /**
     * Helper: Generate a random, secure token string.
     */
    private String generateRandomToken() {
        byte[] randomBytes = new byte[32]; // 256-bit
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
