/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.api.controller.internal;

import com.smartchat.api.adapter.ChatHealthAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ==========================================================
 * 🧩 InternalController
 * ----------------------------------------------------------
 * Internal-only endpoints used by orchestrator components
 * for health verification and inter-service diagnostics.
 * ==========================================================
 */
@RestController
@RequestMapping(value = "/api/internal", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class InternalController {

    private final ChatHealthAdapter chatHealthAdapter;

    @GetMapping("/stomp-ready")
    public ResponseEntity<?> chatHealth() {
        boolean isHealthy = chatHealthAdapter.isRelayHealthy();
        return ResponseEntity.ok(Map.of("stompRelayHealthy", isHealthy));
    }
}
