/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.config.auth;

import com.smartchat.backend.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String CLASS = "[SecurityConfig]";
    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("{} 🔐 Initializing Security Filter Chain...", CLASS);

        http
                // ==========================================================
                // 🔒 Stateless JWT Auth — No Session, No CSRF
                // ==========================================================
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ==========================================================
                // 🔓 Authorization Rules
                // ==========================================================
                .authorizeHttpRequests(auth -> auth

                        // ✅ Preflight requests (Angular)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ✅ Public auth endpoints
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                "/api/auth/google/callback",
                                "/api/auth/google/init"
                        ).permitAll()

                        // ✅ Public docs / health / static / WebSocket / error endpoints
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/error",
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/uploads/**",
                                "/ws-chat/**",
                                "/health"
                        ).permitAll()

                        // 🔒 Everything else requires JWT
                        .anyRequest().authenticated()
                )

                // ==========================================================
                // 🔄 JWT Filter
                // ==========================================================
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // ==========================================================
                // 🚫 Error Handling (401 / 403)
                // ==========================================================
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            log.warn("{} ❌ Unauthorized → {} {}", CLASS, req.getMethod(), req.getRequestURI());
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"Unauthorized\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            log.warn("{} 🚫 Access denied → {} {}", CLASS, req.getMethod(), req.getRequestURI());
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"Forbidden\"}");
                        })
                );

        log.info("{} ✅ Security Filter Chain configured successfully.", CLASS);
        return http.build();
    }

    // ==========================================================
    // 🌍 Global CORS Configuration
    // ==========================================================
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost",
                "http://localhost:*",
                "http://127.0.0.1",
                "http://127.0.0.1:*",
                "https://smartchat.vercel.app",
                "https://smartchat.ai",
                "https://app.smartchat.ai",
                "https://chat.smartchat.ai"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        log.info("{} 🌍 Applied CORS origins: {}", CLASS, config.getAllowedOriginPatterns());
        return source;
    }

    // ==========================================================
    // 🧠 Authentication Manager
    // ==========================================================
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
