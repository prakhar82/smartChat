/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.api.config;

import com.smartchat.api.security.JwtReactiveAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 🔐 SmartChat API Gateway — Reactive Security Configuration (FINAL)
 * ----------------------------------------------------------------
 * ✅ Enables HTTPS + CORS
 * ✅ Allows all requests to route downstream
 * ✅ Adds JWT filter for context enrichment only
 * ❌ Does NOT block or reject unauthenticated users
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtReactiveAuthenticationFilter jwtFilter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        log.info("[SecurityConfig] 🚀 Initializing SmartChat API Gateway security (Reactive WebFlux) — permissive mode");

        return http
                // 🔒 Stateless, no sessions
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

                // 🌍 Global CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ✅ Permit everything (Gateway only routes)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public Google OAuth
                        .pathMatchers(
                                "/api/google/**",
                                "/google/**"
                        ).permitAll()

                        // Contact public (only Google sync entry points)
                        .pathMatchers("/api/contact/google/**").permitAll()

                        // Public auth endpoints
                        .pathMatchers("/api/auth/**", "/auth/**").permitAll()

                        // Docs
                        .pathMatchers(
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Websocket handshake
                        .pathMatchers("/ws-chat/**").permitAll()

                        // Static files
                        .pathMatchers(
                                "/health",
                                "/index.html",
                                "/favicon.ico"
                        ).permitAll()

                        // Everything else simply routes and downstream enforces authentication
                        .anyExchange().permitAll()
                )

                // ⚙️ Add JWT filter (context enrichment only)
                .addFilterAfter(jwtFilter, SecurityWebFiltersOrder.AUTHORIZATION)

                .build();
    }

    /**
     * 🌍 Global CORS config (matches application.yml)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();

        cors.setAllowedOriginPatterns(List.of(
                "https://app.smartchat.ai",
                "https://smartchat.ai",
                "http://localhost:*",
                "https://localhost:*"
        ));

        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));
        cors.setExposedHeaders(List.of("Authorization"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
