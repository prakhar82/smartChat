/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.auth.service;


import com.smartchat.auth.model.InviteToken;
import com.smartchat.auth.model.User;
import com.smartchat.auth.repository.InviteTokenRepository;
import com.smartchat.common.cache.model.CachedInviteToken;
import com.smartchat.common.cache.repository.CachedInviteTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class InviteTokenService {

    private final InviteTokenRepository inviteTokenRepository;
    private final CachedInviteTokenRepository cachedInviteTokenRepository;

    /**
     * Generate a new invite token for a user.
     * Stored in both PostgreSQL and Redis for quick lookup.
     */
    public InviteToken generateToken(User inviter) {
        String tokenValue = generateRandomToken();

        InviteToken token = InviteToken.builder()
                .token(tokenValue)
                .inviter(inviter)
                .used(false)
                .build();

        InviteToken saved = inviteTokenRepository.save(token);

        // Cache in Redis
        cachedInviteTokenRepository.save(
                CachedInviteToken.builder()
                        .token(tokenValue)
                        .inviterId(inviter.getId())
                        .used(false)
                        .expiry(Instant.now().plusSeconds(86400).toEpochMilli()) // 1 day cache
                        .build()
        );

        log.info("[InviteTokenService] 🎟 Generated invite token for inviterId={} ({}...)", inviter.getId(), tokenValue.substring(0, 8));

        return saved;
    }

    /**
     * Validate and mark token as used.
     * Also invalidates Redis cache.
     */
    public InviteToken useToken(String tokenValue) {
        InviteToken token = inviteTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> {
                    log.warn("[InviteTokenService] ❌ Invalid invite token: {}...", tokenValue.substring(0, 8));
                    return new IllegalArgumentException("Invalid invite token");
                });

        if (token.isUsed()) {
            log.warn("[InviteTokenService] ⚠️ Token already used: {}...", tokenValue.substring(0, 8));
            throw new IllegalStateException("Invite token already used");
        }

        token.markUsed();
        InviteToken updated = inviteTokenRepository.save(token);

        // Remove from cache
        cachedInviteTokenRepository.deleteById(tokenValue);

        log.info("[InviteTokenService] ✅ Invite token {}... marked as used", tokenValue.substring(0, 8));
        return updated;
    }

    /**
     * Fast validity check with Redis fallback.
     */
    public boolean isValid(String tokenValue) {
        // 1️⃣ Try Redis first
        CachedInviteToken cached = cachedInviteTokenRepository.findById(tokenValue).orElse(null);
        if (cached != null && !cached.isUsed()) {
            log.debug("[InviteTokenService] ⚡ Cache hit: token={}... valid=true", tokenValue.substring(0, 8));
            return true;
        }

        // 2️⃣ Fallback to PostgreSQL
        boolean valid = inviteTokenRepository.existsByTokenAndUsedFalse(tokenValue);
        log.debug("[InviteTokenService] 🔍 DB check: token={}... valid={}", tokenValue.substring(0, 8), valid);
        return valid;
    }

    private String generateRandomToken() {
        byte[] randomBytes = new byte[32]; // 256-bit token
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
