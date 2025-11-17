/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

import java.util.Collections;

/**
 * ✅ Unified WebFlux configuration for SmartChat API Gateway.
 * <p>
 * - Disables default static resource mappings from /static, /public, /resources.
 * - Provides a fallback SimpleUrlHandlerMapping bean (needed for CORS).
 * - Ensures Spring Cloud Gateway routing and actuator endpoints still work.
 * <p>
 * ⚠️ DO NOT create another SimpleUrlHandlerMapping bean elsewhere.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class GatewayWebFluxConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // ❌ No static resource handlers — Gateway only serves dynamic routes.
    }

    /**
     * Provides an empty handler mapping to satisfy internal WebFlux requirements.
     * This prevents "No handler mapping for CORS" errors.
     */
    @Bean
    @ConditionalOnMissingBean(SimpleUrlHandlerMapping.class)
    public SimpleUrlHandlerMapping simpleUrlHandlerMapping() {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(Integer.MAX_VALUE); // lowest priority
        mapping.setUrlMap(Collections.emptyMap());
        return mapping;
    }
}
