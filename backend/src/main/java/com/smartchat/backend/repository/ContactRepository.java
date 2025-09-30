/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.repository;

import com.smartchat.backend.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByOwnerUserId(Long ownerUserId);

    Optional<Contact> findByOwnerUserIdAndNormalizedPhone(Long ownerUserId, String normalizedPhone);

    Optional<Contact> findByOwnerUserIdAndNormalizedEmail(Long ownerUserId, String normalizedEmail);
}
