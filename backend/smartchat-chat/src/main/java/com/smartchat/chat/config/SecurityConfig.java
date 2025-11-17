/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.config;

import com.smartchat.common.security.SecurityConfigBase;
import com.smartchat.common.security.jwt.JwtAuthenticationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Slf4j
@Configuration
public class SecurityConfig extends SecurityConfigBase {

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        super(jwtFilter, new NoopUserDetailsService());
        log.info("[Chat::SecurityConfig] 🔐 Chat SecurityConfig initialized");
    }

    @Override
    protected void configureAuthorization(
            org.springframework.security.config.annotation.web.configurers
                    .AuthorizeHttpRequestsConfigurer<HttpSecurity>
                    .AuthorizationManagerRequestMatcherRegistry auth
    ) {

        log.info("[Chat::SecurityConfig] Applying Chat specific endpoint rules");

        // 🔓 Public WebSocket handshake endpoints
        auth.requestMatchers(
                "/ws-chat/**",
                "/chat/health",
                "/api/internal/stomp-ready"
        ).permitAll();

        // 🔐 Protected chat REST endpoints
        auth.requestMatchers(
                "/chat/history/**",
                "/chat/send/**",
                "/chat/presence/**"
        ).authenticated();

        // ❗ DO NOT ADD anyRequest() HERE ❗
    }
}
