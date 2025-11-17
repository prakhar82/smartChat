/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ChatMessage (MongoDB Document)
 * ----------------------------------------
 * - Stores messages exchanged between two users
 * - Indexed for fast sender/receiver queries
 * - Compatible with Redis caching and WebSocket broadcasting
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "chat_messages")
@CompoundIndexes({
        @CompoundIndex(name = "sender_receiver_idx", def = "{'senderId': 1, 'receiverId': 1}"),
        @CompoundIndex(name = "receiver_sender_idx", def = "{'receiverId': 1, 'senderId': 1}")
})
public class ChatMessage implements Serializable {

    @Id
    private String id;

    private Long senderId;
    private Long receiverId;

    private String message;
    private String emoji;
    private String fileUrl;
    private String fileName;
    private String status; // SENT / DELIVERED / READ

    @CreatedDate
    private LocalDateTime timestamp;

    @Builder.Default
    private boolean deleted = false;

    /**
     * Automatically initialize message defaults.
     */
    public void onCreate() {
        if (timestamp == null) timestamp = LocalDateTime.now();
        if (status == null) status = "SENT";
    }

    /**
     * Soft-delete message contents safely.
     */
    public void markDeleted() {
        this.message = "This message was deleted";
        this.fileUrl = null;
        this.fileName = null;
        this.emoji = null;
        this.deleted = true;
    }
}
