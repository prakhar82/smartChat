/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String lowerPath = path.toLowerCase();

        log.trace("[JwtAuthFilter] Incoming path: {}", lowerPath);

        if (isPublicPath(lowerPath)) {
            log.trace("[JwtAuthFilter] ⏭️ Skipping JWT filter for public path: {}", lowerPath);
            chain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.trace("[JwtAuthFilter] ⚠️ No Authorization header for path: {}", lowerPath);
            chain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            String username = jwtUtil.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                boolean valid = false;
                try {
                    valid = jwtUtil.isTokenValid(token);
                } catch (Exception ve) {
                    log.warn("[JwtAuthFilter] ❌ Token validation threw: {} (token might be invalid/signature mismatch)", ve.getMessage());
                    // optionally: response.addHeader("X-JWT-Error", ve.getMessage());
                }

                if (valid) {
                    List<String> roles = jwtUtil.extractRoles(token);

                    var authorities = (roles != null && !roles.isEmpty())
                            ? roles.stream().map(SimpleGrantedAuthority::new).toList()
                            : Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

                    var userDetails = new User(username, "", authorities);
                    var authToken = new UsernamePasswordAuthenticationToken(userDetails, token, authorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("[JwtAuthFilter] ✅ Authenticated user={} roles={}", username, roles);
                } else {
                    log.warn("[JwtAuthFilter] ⚠️ Token invalid or expired for user={}", username);
                }
            }
        } catch (Exception e) {
            log.warn("[JwtAuthFilter] ❌ Invalid token: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        String normalized = path.toLowerCase();

        return normalized.contains("/auth/login")
                || normalized.contains("/auth/register")
                || normalized.contains("/auth/refresh")
                || normalized.contains("/auth/verify")
                || normalized.contains("/auth/token")
                || normalized.contains("/api/auth/")
                || normalized.contains("/actuator/")
                || normalized.contains("/swagger-ui")
                || normalized.contains("/v3/api-docs")
                || normalized.contains("/ws-chat");
    }
}
