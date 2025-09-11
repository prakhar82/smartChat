/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 *
 * Purpose:
 *  - Stores a single contact entry uploaded by a user (ownerUserId).
 *  - phoneNumber is the raw contact value; smartChatUserId is populated when that contact
 *    corresponds to a registered SmartChat user.
 *
 * Where to use:
 *  - Saved by ContactService.syncContacts()
 *  - Queried when building matched contact lists for chat UI
 */

package com.smartchat.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contacts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The owner (SmartChat user) who synced this contact.
     */
    private Long ownerUserId;

    /**
     * The contact phone number (normalized ideally).
     */
    private String phoneNumber;

    /**
     * If the contact is also a registered SmartChat user, store their id here.
     * Otherwise null.
     */
    private Long smartChatUserId;
}
