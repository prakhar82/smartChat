/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.auth.JwtUtil;
import com.smartchat.backend.dto.ContactSyncRequest;
import com.smartchat.backend.dto.MatchedContactResponse;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.ContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @PostMapping("/sync")
    public ResponseEntity<?> syncContacts(
            @RequestBody ContactSyncRequest request,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = extractUserId(authHeader);

        // ✅ Always enforce JWT userId
        request.setOwnerUserId(userId);

        log.info("[ContactController] ▶ Sync request received for userId={}", userId);
        contactService.syncContacts(request);
        log.info("[ContactController] ✅ Contacts synced successfully for userId={}", userId);

        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @GetMapping("/matched")
    public ResponseEntity<List<MatchedContactResponse>> getMatchedContacts(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long callerId = extractUserId(authHeader);

        log.info("[ContactController] ▶ Fetching matched contacts for userId={}", callerId);
        List<MatchedContactResponse> matched = contactService.getMatchedContacts(callerId);
        log.info("[ContactController] ✅ Found {} matched contacts for userId={}", matched.size(), callerId);

        return ResponseEntity.ok(matched);
    }

    @GetMapping("/has-contacts")
    public ResponseEntity<Map<String, Boolean>> hasContacts(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = extractUserId(authHeader);
        boolean hasContacts = contactService.userHasContacts(userId);
        log.info("[ContactController] ▶ hasContacts check for userId={} → {}", userId, hasContacts);
        return ResponseEntity.ok(Map.of("hasContacts", hasContacts));
    }

    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing token");
        }
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        return userRepository.findByMobileNumber(username)
                .map(u -> u.getId())
                .orElseThrow(() -> new RuntimeException("User not found for token"));
    }
}
