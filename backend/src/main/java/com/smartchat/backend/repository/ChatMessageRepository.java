/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.repository;

import com.smartchat.backend.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            SELECT cm
            FROM ChatMessage cm
            WHERE cm.senderId = :userId OR cm.receiverId = :userId
            ORDER BY cm.timestamp DESC
            """)
    List<ChatMessage> findLatestMessagesByUser(@Param("userId") Long userId);

    @Query("""
            SELECT cm
            FROM ChatMessage cm
            WHERE cm.senderId = :senderId AND cm.receiverId = :receiverId
            ORDER BY cm.timestamp DESC
            """)
    List<ChatMessage> findBySenderAndReceiver(
            @Param("senderId") Long senderId,
            @Param("receiverId") Long receiverId,
            Pageable pageable
    );

    @Query("""
            SELECT cm
            FROM ChatMessage cm
            WHERE (cm.senderId = :sender1 AND cm.receiverId = :receiver1)
               OR (cm.senderId = :sender2 AND cm.receiverId = :receiver2)
            ORDER BY cm.timestamp DESC
            """)
    List<ChatMessage> findConversationMessages(
            @Param("sender1") Long sender1,
            @Param("receiver1") Long receiver1,
            @Param("sender2") Long sender2,
            @Param("receiver2") Long receiver2,
            Pageable pageable
    );
}
