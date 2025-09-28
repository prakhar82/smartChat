/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.model;

import com.smartchat.backend.dto.ContactSyncRequest;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.util.List;

@Entity
@Table(name = "contacts")
@Data
@NoArgsConstructor
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerUserId;

    private String contactName;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<ContactSyncRequest.PhoneEntry> phones;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<ContactSyncRequest.EmailEntry> emails;

    private Long smartChatUserId;
}
