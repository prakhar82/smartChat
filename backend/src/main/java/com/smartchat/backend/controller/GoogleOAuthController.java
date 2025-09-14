/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.service.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

@RestController
@RequestMapping("/api/oauth/google")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final GoogleOAuthService oauthService;

    @PostMapping("/exchange")
    public ResponseEntity<?> exchangeCode(@RequestBody Map<String, String> body) {
        // expected body: { "ownerUserId": "123", "code": "4/..." }
        if (!body.containsKey("ownerUserId") || !body.containsKey("code")) {
            return ResponseEntity.badRequest().body(Map.of("error", "ownerUserId and code required"));
        }
        Long ownerUserId = Long.parseLong(body.get("ownerUserId"));
        String code = body.get("code");
        oauthService.exchangeCodeForTokens(ownerUserId, code);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/token/{ownerUserId}")
    public ResponseEntity<?> getAccessToken(@PathVariable Long ownerUserId) {
        String token = oauthService.getValidAccessToken(ownerUserId);
        if (token == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("accessToken", token));
    }
}

