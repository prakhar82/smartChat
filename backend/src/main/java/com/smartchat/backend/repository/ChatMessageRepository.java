/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

package com.smartchat.backend.repository;

import com.smartchat.backend.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /*TODO: For speed on large tables
         CREATE INDEX idx_sender_receiver ON chat_messages(sender_id, receiver_id);
        CREATE INDEX idx_timestamp ON chat_messages(timestamp); */

    // Fetch conversation in both directions (latest messages first)
    @Query("SELECT m FROM ChatMessage m " +
            "WHERE (m.senderId = :userId AND m.receiverId = :contactId) " +
            "   OR (m.senderId = :contactId AND m.receiverId = :userId) " +
            "ORDER BY m.timestamp DESC")
    List<ChatMessage> findConversation(
            @Param("userId") Long userId,
            @Param("contactId") Long contactId,
            Pageable pageable
    );
}
