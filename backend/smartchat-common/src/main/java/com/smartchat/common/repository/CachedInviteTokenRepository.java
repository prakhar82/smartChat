/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.repository;

import com.smartchat.common.cache.model.CachedInviteToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CachedInviteTokenRepository extends CrudRepository<CachedInviteToken, String> {

    // Find all tokens for a given inviter
    Iterable<CachedInviteToken> findByInviterId(Long inviterId);

    // Find all tokens that are not used yet
    Iterable<CachedInviteToken> findByUsedFalse();

    // Optional: find valid token
    Optional<CachedInviteToken> findByTokenAndUsedFalse(String token);
}
