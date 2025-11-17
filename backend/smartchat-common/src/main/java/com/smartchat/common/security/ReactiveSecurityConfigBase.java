/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * ==========================================================
 * ⚙️ ReactiveSecurityConfigBase
 * ----------------------------------------------------------
 * Shared security base for SmartChat WebFlux services
 * (used by API Gateway and any reactive microservices).
 * <p>
 * Provides:
 * - Stateless JWT-ready setup
 * - Global CORS configuration
 * - Public endpoint support (via PublicEndpoints.PATHS)
 * - Disabled CSRF, form login, basic auth
 * ==========================================================
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public abstract class ReactiveSecurityConfigBase {

    /**
     * 🔒 Defines the main security filter chain for reactive applications.
     */
    @Bean
    public SecurityWebFilterChain reactiveSecurityFilterChain(ServerHttpSecurity http) {
        log.info("[ReactiveSecurityConfigBase] ⚡ Building reactive security filter chain...");

        return http
                // Disable stateful and form-based mechanisms
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                // Apply CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Route authorization rules
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers(PublicEndpoints.PATHS.toArray(new String[0])).permitAll()
                        .anyExchange().authenticated()
                )

                // Stateless sessions (no persistence of security context)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

                .build();
    }

    /**
     * 🌍 Global CORS configuration for all reactive endpoints.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // NOTE: In production, replace "*" with frontend origin(s)
        corsConfig.setAllowedOrigins(List.of("*"));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        log.info("[ReactiveSecurityConfigBase] 🌐 Global CORS configured for all routes");
        return source;
    }
}
