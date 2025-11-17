/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.service.impl;

import com.smartchat.chat.dto.RecentChatResponse;
import com.smartchat.chat.model.ChatMessage;
import com.smartchat.chat.repository.ChatMessageRepository;
import com.smartchat.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // =========================================================
    // 🧠 1. GET RECENT CHATS (WhatsApp style)
    // =========================================================
    @Override
    public List<RecentChatResponse> getRecentChats(Long userId) {
        try {
            // 1️⃣ Fetch last 200 messages involving this user
            List<ChatMessage> messages = chatMessageRepository.findRecentMessages(
                    userId,
                    PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "timestamp"))
            );

            if (messages.isEmpty()) return List.of();

            // 2️⃣ Map <partnerId → lastMessage>
            Map<Long, ChatMessage> latest = new HashMap<>();

            for (ChatMessage msg : messages) {
                Long partnerId = msg.getSenderId().equals(userId)
                        ? msg.getReceiverId()
                        : msg.getSenderId();

                latest.compute(partnerId, (pid, existing) ->
                        (existing == null || msg.getTimestamp().isAfter(existing.getTimestamp()))
                                ? msg
                                : existing
                );
            }

            // 3️⃣ Convert into DTO
            return latest.entrySet().stream()
                    .map(entry -> {
                        Long partnerId = entry.getKey();
                        ChatMessage last = entry.getValue();

                        return new RecentChatResponse(
                                partnerId.toString(),
                                "User " + partnerId,    // placeholder name
                                "N/A",                  // placeholder mobile
                                true,                   // always registered
                                last.getMessage(),
                                last.getTimestamp()
                        );
                    })
                    .sorted(Comparator.comparing(RecentChatResponse::lastMessageTime).reversed())
                    .toList();

        } catch (Exception e) {
            log.error("[ChatServiceImpl] ❌ Failed loading recent chats: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // =========================================================
    // 💾 2. SAVE & SEND
    // Used by WebSocket controller + REST API
    // =========================================================
    @Override
    public ChatMessage saveAndSend(ChatMessage msg) {
        try {
            if (msg.getTimestamp() == null) {
                msg.setTimestamp(LocalDateTime.now());
            }

            if (msg.getStatus() == null) {
                msg.setStatus("SENT");
            }

            ChatMessage saved = chatMessageRepository.save(msg);

            log.info("[ChatServiceImpl] 💬 Saved message {} → {} (id={})",
                    saved.getSenderId(), saved.getReceiverId(), saved.getId());

            // WebSocket sending (Angular listens automatically)
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(saved.getReceiverId()),
                    "/queue/messages",
                    saved
            );

            // Acknowledge sender
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(saved.getSenderId()),
                    "/queue/status",
                    Map.of("messageId", saved.getId(), "status", "DELIVERED")
            );

            return saved;

        } catch (Exception e) {
            log.error("[ChatServiceImpl] ❌ saveAndSend failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    // =========================================================
    // ✏️ 3. UPDATE STATUS
    // =========================================================
    @Override
    public ChatMessage updateStatus(String messageId, String status) {
        ChatMessage msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Message not found"));

        msg.setStatus(status);
        ChatMessage updated = chatMessageRepository.save(msg);

        log.info("[ChatServiceImpl] 🔄 Message {} status → {}", messageId, status);

        return updated;
    }

    // =========================================================
    // 🗑️ 4. DELETE MESSAGE
    // =========================================================
    @Override
    public ChatMessage deleteMessage(String id, Long userId) {
        ChatMessage msg = chatMessageRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Message not found"));

        if (!Objects.equals(msg.getSenderId(), userId)) {
            throw new IllegalStateException("Unauthorized delete");
        }

        chatMessageRepository.deleteById(id);

        log.info("[ChatServiceImpl] 🗑️ Deleted message {} by user {}", id, userId);

        return msg;
    }

    // =========================================================
    // 📦 5. BROADCAST STATUS (Angular listens to /queue/status)
    // =========================================================
    @Override
    public void broadcastStatus(ChatMessage message) {
        if (message == null || message.getId() == null) {
            log.warn("[ChatServiceImpl] ⚠️ Skipping status broadcast — invalid message");
            return;
        }

        Map<String, Object> payload = Map.of(
                "messageId", message.getId(),
                "status", message.getStatus()
        );

        messagingTemplate.convertAndSendToUser(
                String.valueOf(message.getSenderId()),
                "/queue/status",
                payload
        );

        messagingTemplate.convertAndSendToUser(
                String.valueOf(message.getReceiverId()),
                "/queue/status",
                payload
        );

        log.info("[ChatServiceImpl] 📡 Broadcast status '{}' for message {}",
                message.getStatus(), message.getId());
    }
}
