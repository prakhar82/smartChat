/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.contracts;

import com.smartchat.chat.model.ChatMessage;
import com.smartchat.chat.repository.ChatMessageRepository;
import com.smartchat.common.contracts.ChatContract;
import com.smartchat.common.dto.MatchedContactResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * [ChatContractImpl]
 * ------------------------------------------------------------
 * Provides cross-module chat data via the shared ChatContract interface.
 * Used by other modules (e.g., Contact Service) to access minimal chat info
 * without direct dependency on the chat module.
 * ------------------------------------------------------------
 * ✅ Fetches latest chat messages per user
 * ✅ Builds lightweight MatchedContactResponse objects
 * ✅ Logs key events consistently for cross-service tracing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatContractImpl implements ChatContract {

    private static final String CLASS = "[ChatContractImpl]";
    private final ChatMessageRepository chatMessageRepository;

    // ==========================================================
    // 💬 Find Matched Contacts from Chat Messages
    // ==========================================================
    @Override
    public List<MatchedContactResponse> findMatchedContacts(String userId) {
        log.info("{} ▶ Received request to find matched contacts for userId={}", CLASS, userId);

        Long uid;
        try {
            uid = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            log.error("{} ❌ Invalid userId format: {}", CLASS, userId);
            return List.of();
        }

        List<ChatMessage> messages = chatMessageRepository.findLatestMessagesByUser(uid);
        log.info("{} 💬 Retrieved {} chat messages for userId={}", CLASS, messages.size(), uid);

        if (messages.isEmpty()) {
            log.warn("{} ⚠️ No chat messages found for userId={}", CLASS, uid);
            return List.of();
        }

        // Group messages by unique contactId
        List<Long> contactIds = messages.stream()
                .map(msg -> msg.getSenderId().equals(uid) ? msg.getReceiverId() : msg.getSenderId())
                .distinct()
                .collect(Collectors.toList());

        List<MatchedContactResponse> matchedContacts = new ArrayList<>();

        for (Long contactId : contactIds) {
            // Get the most recent message between user and this contact
            ChatMessage lastMessage = messages.stream()
                    .filter(msg -> msg.getSenderId().equals(contactId) || msg.getReceiverId().equals(contactId))
                    .max((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                    .orElse(null);

            String lastMsgContent = lastMessage != null ? lastMessage.getMessage() : "(no messages yet)";

            matchedContacts.add(MatchedContactResponse.builder()
                    .contactId(String.valueOf(contactId))
                    .contactName("Chat with " + contactId)
                    .lastMessage(lastMsgContent)
                    .phones(List.of())   // chat module doesn't handle phone lists
                    .emails(List.of())   // chat module doesn't handle email lists
                    .matched(true)       // all chat contacts are registered users
                    .canInvite(false)
                    .matchedUserId(contactId)
                    .isOnline(false)     // real-time presence handled by presence service
                    .build());
        }

        log.info("{} ✅ Built {} matched contact responses for userId={}", CLASS, matchedContacts.size(), uid);
        return matchedContacts;
    }
}
