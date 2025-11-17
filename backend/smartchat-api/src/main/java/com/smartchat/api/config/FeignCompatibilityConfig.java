/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.api.config;

import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ==========================================================
 * 🧩 FeignCompatibilityConfig
 * ----------------------------------------------------------
 * Fixes Feign autowiring issue in WebFlux apps.
 * Provides a basic HttpMessageConverters bean
 * for FeignEncoder / FeignDecoder to work properly.
 * ==========================================================
 */
@Configuration
public class FeignCompatibilityConfig {

    @Bean
    public HttpMessageConverters messageConverters() {
        // Empty converters object is enough for Feign
        return new HttpMessageConverters();
    }
}

