/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column(name = "mobile_normalized", nullable = false, unique = true)
    private String mobileNormalized;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "google_access_token")
    private String googleAccessToken;

    @Column(name = "google_refresh_token")
    private String googleRefreshToken;

    @Column(name = "google_token_expiry")
    private Instant googleTokenExpiry;


    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    // Optional: referral relation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_id")
    private User referredBy;

    /**
     * Normalize mobile before insert/update
     */
    @PrePersist
    @PreUpdate
    public void normalizeMobile() {
        if (this.countryCode != null && this.mobileNumber != null) {
            this.mobileNormalized =
                    this.countryCode.replace("+", "") +
                            this.mobileNumber.replaceAll("\\D+", "");
        }
    }

    public List<String> getRolesAsList() {
        if (role == null || role.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(role.trim());
    }

}
