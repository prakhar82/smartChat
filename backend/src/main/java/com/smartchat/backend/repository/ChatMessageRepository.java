/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

/*TODO: For speed on large tables
         CREATE INDEX idx_sender_receiver ON chat_messages(sender_id, receiver_id);
        CREATE INDEX idx_timestamp ON chat_messages(timestamp); */

package com.smartchat.backend.repository;

import com.smartchat.backend.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySenderIdAndReceiverIdOrderByTimestampDesc(Long senderId, Long receiverId, Pageable pageable);

    // OR query for both directions at once
    List<ChatMessage> findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByTimestampDesc(
            Long sender1, Long receiver1,
            Long sender2, Long receiver2,
            Pageable pageable
    );
}


