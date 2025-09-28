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
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByOwnerUserId(Long ownerUserId);

    @Query(
            value = "SELECT * FROM contacts c " +
                    "WHERE c.owner_user_id = :ownerUserId " +
                    "AND EXISTS (SELECT 1 FROM jsonb_array_elements(c.emails) elem " +
                    "            WHERE elem->>'value' = :email)",
            nativeQuery = true
    )
    Optional<Contact> findByOwnerUserIdAndEmailValue(Long ownerUserId, String email);

    @Query(
            value = "SELECT * FROM contacts c " +
                    "WHERE c.owner_user_id = :ownerUserId " +
                    "AND EXISTS (SELECT 1 FROM jsonb_array_elements(c.phones) elem " +
                    "            WHERE elem->>'value' = :phoneValue)",
            nativeQuery = true
    )
    Optional<Contact> findByOwnerUserIdAndPhoneValue(Long ownerUserId, String phoneValue);
}
