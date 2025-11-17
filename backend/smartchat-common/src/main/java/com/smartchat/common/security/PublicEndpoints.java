/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.security;

import java.util.List;

/**
 * ==========================================================
 * 🌐 PublicEndpoints
 * ----------------------------------------------------------
 * Defines URL patterns that bypass authentication across all
 * SmartChat microservices (Auth, Contact, Chat, etc.).
 * <p>
 * Backward-compatible: supports both old `/api/...` and new `/...` routes.
 * ==========================================================
 */
public final class PublicEndpoints {

    private PublicEndpoints() {
    }

    public static final List<String> PATHS = List.of(
            // --- Auth endpoints (public) ---
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/google/**",
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/google/**",

            // --- Contact / Google OAuth ---
            "/api/contact/google/**",
            "/contact/google/**",

            // --- Monitoring & Docs ---
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/uploads/**",

            // --- WebSocket connections ---
            "/ws-chat/**",

            // --- Static & Health ---
            "/health",
            "/index.html",
            "/favicon.ico"
    );
}
