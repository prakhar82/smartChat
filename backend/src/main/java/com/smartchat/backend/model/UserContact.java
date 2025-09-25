/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "user_contacts",
        indexes = {
                @Index(name = "idx_owner_phone", columnList = "owner_user_id, phone_normalized"),
                @Index(name = "idx_owner_email", columnList = "owner_user_id, email")
        }
)
@Data
@NoArgsConstructor
public class UserContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "phone_normalized", length = 30)
    private String phoneNormalized;

    @Column(name = "phone_raw")
    private String phoneRaw;

    @Column(name = "matched_user_id")
    private Long matchedUserId;

    @Column(name = "source")
    private String source;

    @Column(name = "email")
    private String email;

    @Column(name = "label")
    private String label;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
