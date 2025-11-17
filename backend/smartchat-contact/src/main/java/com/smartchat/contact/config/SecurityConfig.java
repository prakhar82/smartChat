/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.config;

import com.smartchat.common.security.SecurityConfigBase;
import com.smartchat.common.security.jwt.JwtAuthenticationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

@Slf4j
@Configuration
public class SecurityConfig extends SecurityConfigBase {

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        super(jwtFilter, new NoopUserDetailsService());  // ✅ No UserDetailsService injected here
        log.info("[ContactSecurityConfig] Loaded without UserDetailsService");
    }

    @Override
    protected void configureAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth
    ) {

        log.info("[ContactSecurityConfig] Applying Contact-specific rules");


        // 🔓 Public endpoints
        auth.requestMatchers(
                "/contact/health",
                "/contact/public/**"
        ).permitAll();

        // 🔐 Protected OAuth steps
        auth.requestMatchers(
                "/contact/google/init-url",
                "/contact/google/token",
                "/contact/google/sync",

                // Contacts & device sync
                "/contact/matched",
                "/contact/sync"
        ).authenticated();

        // ❗ DO NOT ADD anyRequest() HERE ❗
    }

    //.anyRequest().authenticated();

}
