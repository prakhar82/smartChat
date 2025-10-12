/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * [WebConfig]
 * <p>
 * Global CORS (Cross-Origin Resource Sharing) configuration for SmartChat backend.
 * This ensures that the Angular frontend (running on localhost or deployed domain)
 * can communicate with the Spring Boot API securely across origins.
 * <p>
 * Features:
 * - Enables cross-origin requests for allowed SmartChat frontends.
 * - Supports cookies / JWTs via `allowCredentials(true)`.
 * - Covers standard HTTP verbs and preflight OPTIONS.
 * - Easily extendable for production URLs (e.g., app.smartchat.ai).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);
    private static final String CLASS = "[WebConfig]";

    /*@Override
    public void addCorsMappings(CorsRegistry registry) {
        log.info("{} 🌍 Configuring global CORS policy...", CLASS);

        registry.addMapping("/**")
                // ✅ Allowed origins for both dev and production
                .allowedOrigins(
                        "http://localhost:4200",      // Angular dev server
                        "http://localhost",           // Nginx frontend (Docker)
                        "https://smartchat.ai",       // Production (example)
                        "https://app.smartchat.ai"    // Deployed subdomain
                )
                // ✅ Allow common HTTP methods
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // ✅ Accept all headers (frontend sends 'Authorization', 'Content-Type', etc.)
                .allowedHeaders("*")
                // ✅ Enable sending cookies (auth_token, refresh_token)
                .allowCredentials(true)
                // Optional: max age for preflight response (in seconds)
                .maxAge(3600);

        log.info("{} ✅ CORS mappings applied successfully.", CLASS);
    }*/
}
