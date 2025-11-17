/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.auth.config;

import com.smartchat.auth.service.impl.CustomUserDetailsService;
import com.smartchat.common.security.SecurityConfigBase;
import com.smartchat.common.security.jwt.JwtAuthenticationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

@Slf4j
@Configuration
public class SecurityConfig extends SecurityConfigBase {

    public SecurityConfig(
            JwtAuthenticationFilter jwtFilter,
            CustomUserDetailsService customUserDetailsService
    ) {
        super(jwtFilter, customUserDetailsService);
        log.info("[Auth::SecurityConfig] Initialized");
    }

    @Override
    protected void configureAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth
    ) {

        log.info("[Auth::SecurityConfig] Applying Auth specific endpoint rules");

        auth.requestMatchers(
                "/auth/login", "/api/auth/login",
                "/auth/register", "/api/auth/register",
                "/auth/jwt/**", "/api/auth/jwt/**",
                "/auth/refresh", "/api/auth/refresh",
                "/auth/token", "/api/auth/token",
                "/auth/resolve", "/api/auth/resolve"
        ).permitAll();

        // base class adds anyRequest().authenticated()
    }
}
