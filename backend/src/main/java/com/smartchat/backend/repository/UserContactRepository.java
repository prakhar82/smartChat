/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.repository;

import com.smartchat.backend.model.UserContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserContactRepository extends JpaRepository<UserContact, Long> {
    Optional<UserContact> findByOwnerUserIdAndPhoneNormalized(Long ownerUserId, String phoneNormalized);
    List<UserContact> findByOwnerUserId(Long ownerUserId);
}
