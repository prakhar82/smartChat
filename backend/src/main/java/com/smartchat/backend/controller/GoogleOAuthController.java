/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.auth.JwtUtil;
import com.smartchat.backend.repository.jpa.UserRepository;
import com.smartchat.backend.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthController.class);
    private static final String CLASS = "[GoogleOAuthController]";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final GoogleOAuthService oauthService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // ==========================================================
    // STEP 1: Initiate OAuth Flow (handles popup JWT param)
    // ==========================================================
    @GetMapping("/init")
    public void initOAuth(
            @RequestParam(value = "access_token", required = false) String token,
            HttpServletResponse response,
            Principal principal) throws IOException {

        String principalName = principal != null ? principal.getName() : null;
        Long userId = oauthService.resolveUserIdFromPrincipal(principalName);

        // 🧩 If opened via popup without Authorization header, use JWT query param
        if (userId == null && token != null && token.startsWith("ey")) {
            try {
                String username = jwtUtil.extractUsername(token);
                userId = oauthService.resolveUserIdFromPrincipal(username);
                log.info("{} 🧩 Resolved userId={} from access_token param", CLASS, userId);
            } catch (Exception e) {
                log.error("{} ❌ Invalid access_token param: {}", CLASS, e.getMessage());
            }
        }

        if (userId == null) {
            log.warn("{} ⚠️ Unauthorized attempt to initiate OAuth — no valid user context", CLASS);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        String redirectUrl = oauthService.buildAuthUrlWithState(userId);
        log.info("{} 🌐 Redirecting userId={} to Google OAuth: {}", CLASS, userId, redirectUrl);

        response.sendRedirect(redirectUrl);
    }

    // ==========================================================
    // STEP 2: Handle Google callback (popup success/failure)
    // ==========================================================
    @GetMapping("/callback")
    public void callback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpServletResponse response) throws IOException {

        log.info("{} 📥 Received Google OAuth callback with code={} and state={}", CLASS, code, state);

        try {
            Long userId = (state != null && state.matches("\\d+")) ? Long.parseLong(state) : null;
            if (userId == null) {
                throw new IllegalArgumentException("Missing or invalid user state");
            }

            // ✅ Exchange code for tokens and save them
            oauthService.exchangeCodeForTokens(code, userId);

            // Allow popup to close safely
            response.setHeader("Cross-Origin-Opener-Policy", "same-origin-allow-popups");
            response.setHeader("Cross-Origin-Embedder-Policy", "unsafe-none");
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Credentials", "true");

            String htmlResponse = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head><meta charset="UTF-8"><title>Google Sync | SmartChat</title>
                    <style>
                      body { font-family: "Segoe UI", Arial, sans-serif; background: #f9fafc; text-align: center; color: #333; padding: 60px; }
                      .card { background: #fff; border-radius: 12px; box-shadow: 0 4px 10px rgba(0,0,0,0.1); padding: 40px; display: inline-block; }
                      h2 { color: #4caf50; margin-bottom: 10px; }
                    </style></head>
                    <body>
                      <div class="card">
                        <h2>✅ Google Account Linked!</h2>
                        <p>You can close this window. SmartChat will sync your contacts automatically.</p>
                      </div>
                      <script>setTimeout(() => window.close(), 1500);</script>
                    </body></html>
                    """;

            response.setContentType("text/html; charset=UTF-8");
            response.getWriter().write(htmlResponse);

            log.info("{} ✅ OAuth callback handled successfully for userId={}", CLASS, userId);
        } catch (Exception e) {
            log.error("{} ❌ Error during Google OAuth callback: {}", CLASS, e.getMessage(), e);
            response.setContentType("text/html; charset=UTF-8");
            response.getWriter().write("""
                    <html><body style='font-family:sans-serif;text-align:center;'>
                        <h2 style='color:red;'>❌ Google authorization failed!</h2>
                        <p>Please try again.</p>
                        <script>setTimeout(() => window.close(), 2500);</script>
                    </body></html>
                    """);
        }
    }

    // ==========================================================
    // STEP 3: Return current user's valid Google access token
    // ==========================================================
    @GetMapping("/token")
    public ResponseEntity<?> getAccessToken(Principal principal) {
        String principalName = principal != null ? principal.getName() : null;
        Long userId = oauthService.resolveUserIdFromPrincipal(principalName);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized user"));
        }

        String googleToken = oauthService.getValidAccessToken(userId);

        if (googleToken == null) {
            Map<String, Object> body = new HashMap<>();
            body.put("accessToken", null);
            body.put("message", "No Google token found for user");
            return ResponseEntity.ok(body);
        }

        return ResponseEntity.ok(Map.of(
                "accessToken", googleToken,
                "userId", userId
        ));
    }
}
