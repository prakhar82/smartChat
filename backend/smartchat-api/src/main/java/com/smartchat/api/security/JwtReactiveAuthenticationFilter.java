/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.api.security;

import com.smartchat.common.security.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * Reactive JWT filter for Gateway — attaches security context if token is valid.
 * Does NOT reject or block requests — leaves auth enforcement to downstream services.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtReactiveAuthenticationFilter implements WebFilter {

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath().toLowerCase();

        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange); // no token? let downstream handle it
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.extractAllClaims(token);
            String username = claims.getSubject();
            List<String> roles = jwtUtil.extractRoles(token);

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    (roles != null && !roles.isEmpty())
                            ? roles.stream().map(SimpleGrantedAuthority::new).toList()
                            : Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );

            // Inject authentication into the reactive context
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                            Mono.just(new SecurityContextImpl(authentication))
                    ));

        } catch (Exception e) {
            log.debug("[JWT] Ignoring invalid token for {} — {}", path, e.getMessage());
            return chain.filter(exchange);
        }
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/auth/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/")
                || path.equals("/")
                || path.endsWith(".html")
                || path.endsWith(".ico");
    }
}
