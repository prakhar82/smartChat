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
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * [WebConfigDev]
 * <p>
 * CORS configuration for the SmartChat development environment.
 * Allows localhost-based origins for Angular or frontend containers.
 */
@Configuration
@Profile("dev")
public class WebConfigDev implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfigDev.class);
    private static final String CLASS = "[WebConfigDev]";

    /*@Override
    public void addCorsMappings(CorsRegistry registry) {
        log.info("{} 🌍 Enabling development CORS settings...", CLASS);

        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:4200",    // Angular dev server
                        "http://localhost",         // Nginx dev container
                        "http://127.0.0.1"          // Local testing
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        log.info("{} ✅ Development CORS configured successfully.", CLASS);
    }*/
}
