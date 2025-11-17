/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.repository;

import com.smartchat.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    @Query("{ '$or': [ { 'senderId': ?0 }, { 'receiverId': ?0 } ] }")
    List<ChatMessage> findLatestMessagesByUser(Long userId);

    @Query("{ 'senderId': ?0, 'receiverId': ?1 }")
    List<ChatMessage> findBySenderAndReceiver(Long senderId, Long receiverId, Pageable pageable);

    @Query("{ '$or': [ { 'senderId': ?0, 'receiverId': ?1 }, { 'senderId': ?2, 'receiverId': ?3 } ] }")
    List<ChatMessage> findConversationMessages(Long sender1, Long receiver1, Long sender2, Long receiver2, Pageable pageable);

    @Query("{ '$or': [ { 'senderId': ?0 }, { 'receiverId': ?0 } ] }")
    List<ChatMessage> findRecentMessages(Long userId, Pageable pageable);

}
