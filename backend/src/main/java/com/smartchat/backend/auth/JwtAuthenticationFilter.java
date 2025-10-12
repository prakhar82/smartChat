/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ==========================================================
 * ✅ JwtAuthenticationFilter
 * ----------------------------------------------------------
 * Handles JWT extraction and authentication.
 * - Skips public endpoints (login, register, refresh, callback)
 * - Validates tokens for secured endpoints
 * - Sets authenticated user in SecurityContext
 * ==========================================================
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String CLASS = "[JwtAuthenticationFilter]";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        final String path = request.getRequestURI();
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null) {
            // 🔁 fallback for lowercase header key (some proxies)
            authHeader = request.getHeader("authorization");
        }

        log.trace("{} 🔍 Checking request: {} | Header: {}", CLASS, path, authHeader);

        // ==========================================================
        // ⏭ Skip JWT validation for public routes
        // ==========================================================
        if (isPublicPath(path)) {
            log.trace("{} 🟢 Public path → skipping auth check: {}", CLASS, path);
            chain.doFilter(request, response);
            return;
        }

        // ==========================================================
        // 🔹 Extract JWT token
        // ==========================================================
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("{} ⚠️ Missing or invalid Authorization header on {}", CLASS, path);
            chain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);
        String username;

        try {
            username = jwtUtil.extractUsername(token);
        } catch (Exception e) {
            log.warn("{} ❌ Invalid token: {}", CLASS, e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        // ==========================================================
        // 🔹 Authenticate user if valid token and not already set
        // ==========================================================
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(token, username)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("{} ✅ Authenticated user: {}", CLASS, username);
                } else {
                    log.warn("{} ⚠️ Token invalid or expired for {}", CLASS, username);
                }
            } catch (Exception e) {
                log.error("{} ❌ Error during authentication: {}", CLASS, e.getMessage());
            }
        }

        log.trace("{} 🔁 Continuing filter chain. Authenticated user: {}",
                CLASS,
                SecurityContextHolder.getContext().getAuthentication() != null
                        ? SecurityContextHolder.getContext().getAuthentication().getName()
                        : "none");

        chain.doFilter(request, response);
    }

    // ==========================================================
    // 🔹 Public Path Matcher
    // ==========================================================
    private boolean isPublicPath(String path) {
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/google/callback")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator")
                || path.startsWith("/uploads/")
                || path.startsWith("/ws-chat")
                || path.equals("/")
                || path.equals("/index.html");
    }
    
}
