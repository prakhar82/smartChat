/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.auth.JwtUtil;
import com.smartchat.backend.dto.RecentChatResponse;
import com.smartchat.backend.model.ChatMessage;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.jpa.UserRepository;
import com.smartchat.backend.repository.mongo.ChatMessageRepository;
import com.smartchat.backend.service.ChatService;
import com.smartchat.backend.service.cache.ChatCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ==========================================================
 * ✅ ChatController
 * ----------------------------------------------------------
 * Handles:
 * - Fetching recent chats
 * - Fetching chat history
 * - Sending messages
 * - Updating message status
 * - Uploading files
 * - Deleting messages
 * <p>
 * Uses a robust JWT resolver that supports:
 * - Email or mobile-based tokens
 * - Direct `userId` claim (preferred)
 * ==========================================================
 */
@Slf4j
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository chatRepo;
    private final ChatCacheService chatCache;
    private final ChatService chatService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Value("${chat.history.limit:30}")
    private int historyLimit;

    // ==========================================================
    // 🔹 Fetch recent chat list
    // ==========================================================
    @GetMapping("/recent")
    public ResponseEntity<?> recentChats(@RequestHeader("Authorization") String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            log.info("[ChatController] ▶ Fetching recent chats for userId={}", userId);

            List<RecentChatResponse> chats = chatService.getRecentChats(userId);
            return ResponseEntity.ok(chats);

        } catch (RuntimeException e) {
            log.error("[ChatController] ❌ Failed to load recent chats: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "status", 500));
        }
    }

    // ==========================================================
    // 🔹 Fetch chat history between two users
    // ==========================================================
    @GetMapping("/{contactId}")
    public ResponseEntity<?> getChatHistory(@PathVariable Long contactId,
                                            @RequestHeader("Authorization") String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            log.info("[ChatController] ▶ Fetching chat history for userId={} with contactId={}", userId, contactId);

            List<ChatMessage> cached = chatCache.get(userId, contactId);
            if (!cached.isEmpty()) {
                log.debug("[ChatController] 💾 Returning {} cached messages for {} <-> {}", cached.size(), userId, contactId);
                return ResponseEntity.ok(cached);
            }

            List<ChatMessage> dbMessages = chatRepo.findConversationMessages(
                    userId, contactId,
                    contactId, userId,
                    PageRequest.of(0, historyLimit)
            );

            chatCache.put(userId, contactId, dbMessages);
            log.info("[ChatController] ✅ Loaded {} messages from DB for {} <-> {}", dbMessages.size(), userId, contactId);

            return ResponseEntity.ok(dbMessages);
        } catch (Exception e) {
            log.error("[ChatController] ❌ Failed to fetch chat history: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================================
    // 🔹 Send message
    // ==========================================================
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody ChatMessage message,
                                         @RequestHeader("Authorization") String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            message.setSenderId(userId);
            log.info("[ChatController] 🚀 Sending message from userId={} to userId={}", message.getSenderId(), message.getReceiverId());

            ChatMessage saved = chatService.saveAndSend(message);
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("[ChatController] ❌ Failed to send message: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================================
    // 🔹 Update message status
    // ==========================================================
    @PatchMapping("/status/{id}")
    public ChatMessage updateStatus(@PathVariable String id,
                                    @RequestBody Map<String, String> body) {
        String status = body.get("status");
        log.info("[ChatController] ✏️ Updating status for messageId={} to {}", id, status);
        return chatService.updateStatus(id, status);
    }

    // ==========================================================
    // 🔹 Upload attachment
    // ==========================================================
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("senderId") Long senderId,
            @RequestParam("receiverId") Long receiverId) {
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get("uploads");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            ChatMessage msg = new ChatMessage();
            msg.setSenderId(senderId);
            msg.setReceiverId(receiverId);
            msg.setFileName(file.getOriginalFilename());
            msg.setFileUrl("/uploads/" + fileName);
            msg.setTimestamp(LocalDateTime.now());
            msg.setStatus("SENT");

            ChatMessage saved = chatService.saveAndSend(msg);
            log.info("[ChatController] 📁 File uploaded: senderId={} -> receiverId={}, messageId={}", senderId, receiverId, saved.getId());

            return ResponseEntity.ok(Map.of(
                    "messageId", saved.getId(),
                    "fileUrl", saved.getFileUrl()
            ));
        } catch (IOException e) {
            log.error("[ChatController] ❌ File upload failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File upload failed: " + e.getMessage()));
        }
    }

    // ==========================================================
    // 🔹 Delete message
    // ==========================================================
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable String id,
                                           @RequestHeader("Authorization") String authHeader) {
        try {
            Long requesterId = resolveUserId(authHeader);
            log.info("[ChatController] 🗑 Delete request: messageId={} by userId={}", id, requesterId);

            ChatMessage deleted = chatService.deleteMessage(id, requesterId);
            return ResponseEntity.ok(deleted);
        } catch (Exception e) {
            log.error("[ChatController] ❌ Delete failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================================
    // 🔹 Unified JWT User Resolver
    // ==========================================================
    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        // ✅ Prefer the userId claim directly if available
        Long userId = jwtUtil.extractUserId(token);
        if (userId != null) {
            return userId;
        }

        // ⚙️ Fallback: extract subject (email or mobile)
        String subject = jwtUtil.extractUsername(token);
        if (subject == null) {
            throw new RuntimeException("Token missing subject");
        }

        Optional<User> userOpt = userRepository.findByMobileNumber(subject);
        if (userOpt.isEmpty() && subject.contains("@")) {
            userOpt = userRepository.findByEmail(subject);
        }

        return userOpt
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("User not found for token"));
    }
}
