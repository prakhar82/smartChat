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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
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

        InviteToken saved = inviteTokenRepository.save(token);

        log.info("[InviteTokenService] 🎟 Generated new invite token for inviterId={} (token={}...)",
                inviter.getId(), tokenValue.substring(0, 8));

        return saved;
    }

    /**
     * Validate and mark token as used.
     */
    public InviteToken useToken(String tokenValue) {
        InviteToken token = inviteTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> {
                    log.warn("[InviteTokenService] ❌ Invalid invite token attempted: {}...", tokenValue.substring(0, 8));
                    return new IllegalArgumentException("Invalid invite token");
                });

        if (token.isUsed()) {
            log.warn("[InviteTokenService] ⚠ Attempted reuse of already used token={}...", tokenValue.substring(0, 8));
            throw new IllegalStateException("Invite token already used");
        }

        token.markUsed();
        InviteToken updated = inviteTokenRepository.save(token);

        log.info("[InviteTokenService] ✅ Invite token={}... successfully marked as used", tokenValue.substring(0, 8));

        return updated;
    }

    /**
     * Check if token exists and is not yet used.
     */
    public boolean isValid(String tokenValue) {
        boolean valid = inviteTokenRepository.existsByTokenAndUsedFalse(tokenValue);
        log.debug("[InviteTokenService] 🔎 Token={}... valid={}", tokenValue.substring(0, 8), valid);
        return valid;
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
