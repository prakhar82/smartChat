/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * ContactController (Handles local/manual contacts (non-Google).)
 *
 * Purpose:
 *  - /api/contacts/sync        → accept device contacts and persist
 *  - /api/contacts/matched     → validate contact exist in DB
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.auth.JwtUtil;
import com.smartchat.backend.dto.ContactSyncRequest;
import com.smartchat.backend.dto.MatchedContactResponse;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;


    @PostMapping("/sync")
    public ResponseEntity<?> syncContacts(@RequestBody ContactSyncRequest request,
                                          @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader); // implement this
        if (!userId.equals(request.getOwnerUserId())) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        contactService.syncContacts(request);
        return ResponseEntity.ok(Map.of("status", "success"));
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


    @GetMapping("/matched")
    public ResponseEntity<List<MatchedContactResponse>> getMatchedContacts(@RequestParam Long userId) {

        List<MatchedContactResponse> matched = contactService.getMatchedContacts(userId);
        return ResponseEntity.ok(matched);
    }


}