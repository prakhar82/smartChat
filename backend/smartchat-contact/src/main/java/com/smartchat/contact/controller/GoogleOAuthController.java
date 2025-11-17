/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.controller;

import com.smartchat.common.contracts.AuthContract;
import com.smartchat.contact.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.Principal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * GOOGLE OAUTH CONTROLLER — NOW FULLY FIXED
 * Supports:
 * ✔ /init-url → JSON OAuth URL (safe for Angular)
 * ✔ /init → real redirect (popup only)
 * ✔ /callback → code exchange + HTML close popup
 * ✔ /token → return cached Google access token
 */
@Slf4j
@RestController
@RequestMapping("/contact/google")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private static final String CLASS = "[GoogleOAuthController]";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final GoogleOAuthService oauthService;
    private final AuthContract authContract;

    // ============================================================================
    // 1️⃣ Angular-safe endpoint → Returns JSON only (NO redirect)
    // ============================================================================
    @GetMapping("/init-url")
    public ResponseEntity<?> getInitUrl(
            @RequestParam(value = "access_token", required = false) String jwtToken,
            Principal principal
    ) {
        log.info("{} ▶ Request for OAuth JSON URL", CLASS);

        Long userId = oauthService.resolveUserIdFromPrincipal(
                principal != null ? principal.getName() : null
        );

        // Fallback for popup JWT
        if (userId == null && jwtToken != null && jwtToken.startsWith("ey")) {
            try {
                var dto = authContract.getUserByToken(jwtToken);
                if (dto != null && dto.getId() != null) {
                    userId = Long.parseLong(dto.getId());
                }
            } catch (Exception e) {
                log.error("{} ❌ Failed resolving user from JWT param", CLASS, e);
            }
        }

        if (userId == null) {
            log.warn("{} ⚠ Cannot resolve user for /init-url", CLASS);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "unauthorized"));
        }

        String authUrl = oauthService.buildAuthUrlWithState(userId);
        log.info("{} 🌐 Returning JSON authUrl for userId={}", CLASS, userId);

        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    // ============================================================================
    // 2️⃣ Popup redirect (window.open → OK)
    // ============================================================================
    @GetMapping("/init")
    public void initOAuth(
            @RequestParam(value = "access_token", required = false) String jwtToken,
            HttpServletResponse response,
            Principal principal
    ) throws IOException {

        log.info("{} ▶ Popup OAuth redirect", CLASS);

        Long userId = oauthService.resolveUserIdFromPrincipal(
                principal != null ? principal.getName() : null);

        // JWT fallback
        if (userId == null && jwtToken != null && jwtToken.startsWith("ey")) {
            try {
                var dto = authContract.getUserByToken(jwtToken);
                if (dto != null && dto.getId() != null) {
                    userId = Long.parseLong(dto.getId());
                }
            } catch (Exception e) {
                log.error("{} ❌ Popup JWT resolve error", CLASS, e);
            }
        }

        if (userId == null) {
            log.warn("{} ⚠ Unauthorized popup OAuth init", CLASS);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        String redirectUrl = oauthService.buildAuthUrlWithState(userId);
        log.info("{} 🌐 Redirecting to {}", CLASS, redirectUrl);

        response.sendRedirect(redirectUrl);
    }

    // ============================================================================
    // 3️⃣ Callback from Google (exchange tokens)
    // ============================================================================
    @GetMapping("/callback")
    public void callback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpServletResponse response
    ) throws IOException {

        log.info("{} 📥 Callback received: state={}", CLASS, state);

        try {
            Long userId = (state != null && state.matches("\\d+"))
                    ? Long.parseLong(state)
                    : null;

            if (userId == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid state");
                return;
            }

            boolean success = oauthService.exchangeCodeForTokens(code, String.valueOf(userId));
            if (!success) {
                response.setContentType("text/html");
                response.getWriter().write("""
                        <h2 style='color:red;text-align:center;'>Google Authorization Failed</h2>
                        <script>setTimeout(()=>window.close(),1500);</script>
                        """);
                return;
            }

            // Success HTML
            response.setContentType("text/html");
            response.getWriter().write("""
                    <h2 style='color:green;text-align:center;'>Google Connected!</h2>
                    <p>You may close this window.</p>
                    <script>setTimeout(()=>window.close(),1200);</script>
                    """);

            log.info("{} ✅ Token exchange successful for userId={}", CLASS, userId);

        } catch (Exception e) {
            log.error("{} ❌ Callback error", CLASS, e);
        }
    }

    // ============================================================================
    // 4️⃣ Return Google Access Token
    // ============================================================================
    @GetMapping("/token")
    public ResponseEntity<?> getToken(
            Principal principal,
            @RequestParam(value = "access_token", required = false) String jwtToken
    ) {
        log.info("{} ▶ /token request", CLASS);

        Long userId = oauthService.resolveUserIdFromPrincipal(
                principal != null ? principal.getName() : null
        );

        // Popup fallback
        if (userId == null && jwtToken != null) {
            try {
                var dto = authContract.getUserByToken(jwtToken);
                if (dto != null && dto.getId() != null) {
                    userId = Long.parseLong(dto.getId());
                }
            } catch (Exception e) {
                log.error("{} ❌ JWT resolve error", CLASS, e);
            }
        }

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "unauthorized"));
        }

        String token = oauthService.getValidAccessToken(userId);

        // ✅ FIXED: MUST NOT use Map.of() (null not allowed)
        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("userId", userId);
        resp.put("issuedAt", FORMATTER.format(java.time.Instant.now()));
        resp.put("accessToken", token); // SAFE even if null

        return ResponseEntity.ok(resp);
    }

}
