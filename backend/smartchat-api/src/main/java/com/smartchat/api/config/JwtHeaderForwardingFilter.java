/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * Global filter which ensures Authorization header is preserved and forwarded
 * to downstream services. Acts as a safety net against other filters or route rewriting.
 */
@Configuration
@Slf4j
public class JwtHeaderForwardingFilter {

    @Bean
    public GlobalFilter forwardAuthorizationHeader() {
        return (exchange, chain) -> {
            ServerWebExchange requestExchange = exchange;
            String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (auth != null && !auth.isBlank()) {
                log.debug("[JwtHeaderForwardingFilter] Forwarding Authorization header to downstream");
                ServerWebExchange mutated = exchange.mutate()
                        .request(builder -> builder.header(HttpHeaders.AUTHORIZATION, auth))
                        .build();
                return chain.filter(mutated);
            } else {
                log.trace("[JwtHeaderForwardingFilter] No Authorization header present on request");
                return chain.filter(requestExchange);
            }
        };
    }
}
