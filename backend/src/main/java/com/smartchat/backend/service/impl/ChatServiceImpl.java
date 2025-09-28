/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service.impl;

import com.smartchat.backend.dto.RecentChatResponse;
import com.smartchat.backend.model.ChatMessage;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.ChatMessageRepository;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.ChatService;
import com.smartchat.backend.service.cache.ChatCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatRepo;
    private final ChatCacheService chatCache;
    private final UserRepository userRepo;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public List<RecentChatResponse> getRecentChats(Long userId) {
        log.info("[ChatServiceImpl] ▶ Fetching recent chats for userId={}", userId);
        List<ChatMessage> latest = chatRepo.findLatestMessagesByUser(userId);

        List<RecentChatResponse> response = latest.stream().map(msg -> {
            Long contactId = msg.getSenderId().equals(userId) ? msg.getReceiverId() : msg.getSenderId();
            User contact = userRepo.findById(contactId).orElse(null);
            return new RecentChatResponse(
                    String.valueOf(contactId),
                    contact != null ? contact.getFirstName() : "Unknown",
                    contact != null ? contact.getMobileNormalized() : null,
                    contact != null,
                    msg.getMessage(),
                    msg.getTimestamp()
            );
        }).toList();

        log.info("[ChatServiceImpl] ✅ Found {} recent chats for userId={}", response.size(), userId);
        return response;
    }

    @Transactional
    @Override
    public ChatMessage saveAndSend(ChatMessage msg) {
        log.info("[ChatServiceImpl] ▶ Saving message from userId={} to userId={} (type={})",
                msg.getSenderId(),
                msg.getReceiverId(),
                msg.getFileUrl() != null ? "FILE" : "TEXT"
        );

        ChatMessage saved = chatRepo.save(msg);
        log.debug("[ChatServiceImpl] 💾 Message persisted with id={} at {}", saved.getId(), saved.getTimestamp());

        chatCache.pushMessageToCache(saved.getSenderId(), saved.getReceiverId(), saved);
        log.info("[ChatServiceImpl] ✅ Message pushed to cache (messageId={} senderId={} -> receiverId={})",
                saved.getId(), saved.getSenderId(), saved.getReceiverId());

        // Notify receiver
        messagingTemplate.convertAndSendToUser(
                String.valueOf(saved.getReceiverId()),
                "/queue/messages",
                saved
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getConversation(Long senderId, Long receiverId, int limit) {
        log.info("[ChatServiceImpl] ▶ Fetching conversation senderId={} <-> receiverId={}, limit={}", senderId, receiverId, limit);

        List<ChatMessage> messages = chatRepo.findConversationMessages(
                senderId, receiverId,
                receiverId, senderId,
                PageRequest.of(0, limit)
        );

        if (!messages.isEmpty()) {
            log.debug("[ChatServiceImpl] 💬 Conversation loaded: {} messages (firstId={}, lastId={})",
                    messages.size(), messages.get(0).getId(), messages.get(messages.size() - 1).getId());
        } else {
            log.debug("[ChatServiceImpl] 💬 No messages found for conversation {} <-> {}", senderId, receiverId);
        }

        return messages;
    }

    @Transactional
    @Override
    public ChatMessage updateStatus(Long messageId, String status) {
        log.info("[ChatServiceImpl] ▶ Updating message status: messageId={}, newStatus={}", messageId, status);

        if (!List.of("SENT", "DELIVERED", "READ").contains(status)) {
            log.error("[ChatServiceImpl] ❌ Invalid status value: {}", status);
            throw new IllegalArgumentException("Invalid status value: " + status);
        }

        ChatMessage message = chatRepo.findById(messageId)
                .orElseThrow(() -> {
                    log.error("[ChatServiceImpl] ❌ Message not found: {}", messageId);
                    return new IllegalArgumentException("Message not found: " + messageId);
                });

        message.setStatus(status);
        ChatMessage updated = chatRepo.save(message);
        log.debug("[ChatServiceImpl] 💾 Status updated in DB: messageId={}, status={}", messageId, status);

        chatCache.updateMessageStatusInCache(updated);
        log.info("[ChatServiceImpl] ✅ Cache updated for messageId={} with status={}", messageId, status);

        return updated;
    }

    @Transactional
    @Override
    public ChatMessage deleteMessage(Long messageId, Long requesterId) {
        log.info("[ChatServiceImpl] ▶ Soft-deleting messageId={} requested by userId={}", messageId, requesterId);

        ChatMessage message = chatRepo.findById(messageId)
                .orElseThrow(() -> {
                    log.error("[ChatServiceImpl] ❌ Message not found: {}", messageId);
                    return new IllegalArgumentException("Message not found: " + messageId);
                });

        if (!message.getSenderId().equals(requesterId)) {
            log.error("[ChatServiceImpl] ❌ Unauthorized delete attempt: userId={} tried to delete messageId={} owned by {}",
                    requesterId, messageId, message.getSenderId());
            throw new IllegalArgumentException("You can only delete your own messages");
        }

        // Soft delete
        message.setMessage("This message was deleted");
        message.setFileUrl(null);
        message.setFileName(null);
        message.setEmoji(null);

        ChatMessage updated = chatRepo.save(message);
        chatCache.updateMessageStatusInCache(updated);

        log.info("[ChatServiceImpl] 🗑 Message soft-deleted: messageId={} by userId={}", messageId, requesterId);

        // Notify both sender and receiver
        Map<String, Object> payload = Map.of(
                "type", "DELETE",
                "messageId", messageId,
                "message", "This message was deleted"
        );

        messagingTemplate.convertAndSendToUser(
                String.valueOf(updated.getSenderId()), "/queue/messages", payload);
        messagingTemplate.convertAndSendToUser(
                String.valueOf(updated.getReceiverId()), "/queue/messages", payload);

        return updated;
    }
}
