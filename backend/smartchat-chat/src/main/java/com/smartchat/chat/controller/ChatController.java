/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.controller;

import com.smartchat.chat.dto.RecentChatResponse;
import com.smartchat.chat.model.ChatMessage;
import com.smartchat.chat.repository.ChatMessageRepository;
import com.smartchat.chat.service.ChatService;
import com.smartchat.chat.service.cache.ChatCacheService;
import com.smartchat.common.contracts.AuthContract;
import com.smartchat.common.contracts.JwtFeignClient;
import com.smartchat.common.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

;

/**
 * ==========================================================
 * 💬 ChatController (REST)
 * ----------------------------------------------------------
 * Handles chat retrieval, sending, status updates, file uploads,
 * and deletion over REST endpoints.
 * Works in sync with WebSocket messaging layer.
 * ==========================================================
 */
@Slf4j
@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository chatRepo;
    private final ChatCacheService chatCache;
    private final ChatService chatService;
    private final AuthContract authContract;
    private final JwtFeignClient jwtFeignClient;


    @Value("${chat.history.limit:30}")
    private int historyLimit;

    @Value("${uploads.directory:uploads}")
    private String uploadsDir;

    // ==========================================================
    // 🕓 Recent Conversations
    // ==========================================================
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentChats(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            List<RecentChatResponse> chats = chatService.getRecentChats(userId);
            log.debug("[ChatController] ✅ Returning {} recent chats for userId={}", chats.size(), userId);
            return ResponseEntity.ok(chats);
        } catch (Exception e) {
            log.error("[ChatController] ❌ Failed to fetch recent chats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================================
    // 💬 Chat History
    // ==========================================================
    @GetMapping("/{contactId}")
    public ResponseEntity<?> getChatHistory(@PathVariable Long contactId,
                                            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            List<ChatMessage> cached = chatCache.get(userId, contactId);

            if (!cached.isEmpty()) {
                log.trace("[ChatController] ⚡ Returning cached chat history ({} messages) for userId={}↔{}",
                        cached.size(), userId, contactId);
                return ResponseEntity.ok(cached);
            }

            List<ChatMessage> dbMessages = chatRepo.findConversationMessages(
                    userId, contactId, contactId, userId, PageRequest.of(0, historyLimit));
            chatCache.put(userId, contactId, dbMessages);

            log.debug("[ChatController] 🧠 Fetched {} messages from DB for userId={}↔{}",
                    dbMessages.size(), userId, contactId);
            return ResponseEntity.ok(dbMessages);

        } catch (Exception e) {
            log.error("[ChatController] ❌ getChatHistory: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================================
    // ✉️ Send Message
    // ==========================================================
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody ChatMessage message,
                                         @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            message.setSenderId(userId);
            ChatMessage saved = chatService.saveAndSend(message);
            log.info("[ChatController] 💬 Message {} sent from {} → {}", saved.getId(), saved.getSenderId(), saved.getReceiverId());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("[ChatController] ❌ sendMessage: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================================
    // 📬 Update Message Status
    // ==========================================================
    @PatchMapping("/status/{id}")
    public ResponseEntity<?> updateStatus(@PathVariable String id,
                                          @RequestBody Map<String, String> body) {
        try {
            String status = body.get("status");
            if (status == null || status.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "Missing status"));

            ChatMessage updated = chatService.updateStatus(id, status);
            chatService.broadcastStatus(updated);

            log.debug("[ChatController] 🟢 Message {} status updated to {}", id, status);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("[ChatController] ❌ updateStatus: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================================
    // 📎 Upload Attachment
    // ==========================================================
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam("senderId") Long senderId,
                                        @RequestParam("receiverId") Long receiverId) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Empty file upload"));
            }

            String originalName = Paths.get(file.getOriginalFilename()).getFileName().toString();
            String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String fileName = UUID.randomUUID() + "_" + safeName;

            Path uploadPath = Paths.get(uploadsDir);
            if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            ChatMessage msg = new ChatMessage();
            msg.setSenderId(senderId);
            msg.setReceiverId(receiverId);
            msg.setFileName(originalName);
            msg.setFileUrl("/uploads/" + fileName);
            msg.setTimestamp(LocalDateTime.now());
            msg.setStatus("SENT");

            ChatMessage saved = chatService.saveAndSend(msg);
            log.info("[ChatController] 📎 File uploaded by {} → {} ({} bytes)", senderId, receiverId, file.getSize());

            return ResponseEntity.ok(Map.of(
                    "messageId", saved.getId(),
                    "fileUrl", saved.getFileUrl()
            ));
        } catch (IOException e) {
            log.error("[ChatController] ❌ File upload failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload file"));
        }
    }

    // ==========================================================
    // 🗑️ Delete Message
    // ==========================================================
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable String id,
                                           @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        try {
            Long requesterId = resolveUserId(authHeader);
            ChatMessage deleted = chatService.deleteMessage(id, requesterId);
            log.info("[ChatController] 🗑️ Message {} deleted by user {}", id, requesterId);
            return ResponseEntity.ok(deleted);
        } catch (Exception e) {
            log.error("[ChatController] ❌ deleteMessage: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================================
    // 🧩 Resolve User ID from JWT
    // ==========================================================
    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new IllegalArgumentException("Missing or invalid Authorization header");

        String token = authHeader.substring(7);

        Long userId = jwtFeignClient.extractUserId(token).getBody();
        if (userId != null) return userId;

        String emailAddress = jwtFeignClient.extractUsername(token).getBody();
        String mobileNumber = jwtFeignClient.extractMobile(token).getBody();

        if (emailAddress == null) throw new IllegalArgumentException("Token missing subject");

        Optional<UserDTO> userOpt = Optional.ofNullable(
                emailAddress.contains("@")
                        ? authContract.getUserByEmail(emailAddress)
                        : authContract.getUserByMobile(mobileNumber)
        );

        // ✅ convert String → Long safely before returning
        return userOpt.map(UserDTO::getId)
                .map(id -> {
                    try {
                        return Long.parseLong(id);
                    } catch (NumberFormatException e) {
                        throw new IllegalStateException("Invalid user ID format: " + id);
                    }
                })
                .orElseThrow(() -> new IllegalStateException("User not found for token"));
    }

}
