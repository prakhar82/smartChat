/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 🧠 Simple reactive logging filter for SmartChat API Gateway.
 * Logs incoming and outgoing requests in a non-blocking way.
 * <p>
 * Controlled by: logging.level.com.smartchat.api.config.SimpleGatewayLoggingFilter=DEBUG
 */
@Component
public class SimpleGatewayLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SimpleGatewayLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getURI().getPath();

        log.debug("[Gateway] ▶ Incoming {} {}", method, path);

        return chain.filter(exchange)
                .doOnSuccess(ignored -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    int code = status != null ? status.value() : 0;
                    log.debug("[Gateway] ⬅ Responded {} for {} {}", code, method, path);
                })
                .doOnError(ex -> log.warn("[Gateway] ❌ Error on {} {} → {}", method, path, ex.getMessage()))
                .doOnCancel(() -> log.warn("[Gateway] ⚠ Request cancelled: {} {}", method, path))
                .onErrorResume(ex -> {
                    // Ensure logging and proper completion if something breaks mid-chain
                    log.error("[Gateway] 💥 Unhandled exception during {} {} → {}", method, path, ex.getMessage(), ex);
                    return Mono.empty();
                });
    }

    @Override
    public int getOrder() {
        return -1; // Run early in the filter chain
    }
}
