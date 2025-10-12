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

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * [WebConfigProd]
 * <p>
 * Dynamically applies CORS settings depending on environment:
 * - ✅ Local / Docker: allows localhost, 127.0.0.1, and Vercel preview
 * - 🔒 Cloud / Production: allows only SmartChat verified domains
 */
@Configuration
@Profile("prod")
public class WebConfigProd implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfigProd.class);
    private static final String CLASS = "[WebConfigProd]";

    /*@Override
    public void addCorsMappings(CorsRegistry registry) {
        boolean runningInDocker = isRunningInDocker();
        boolean runningOnLocalhost = isLocalEnvironment();

        List<String> allowedOrigins;

        if (runningInDocker || runningOnLocalhost) {
            allowedOrigins = Arrays.asList(
                    "http://localhost",
                    "http://localhost:*",
                    "http://127.0.0.1",
                    "http://127.0.0.1:*",
                    "https://smartchat.vercel.app" // allow preview frontend
            );
            log.info("{} 🧩 Detected Docker/local environment → relaxed CORS mode.", CLASS);
        } else {
            allowedOrigins = Arrays.asList(
                    "https://smartchat.ai",
                    "https://app.smartchat.ai",
                    "https://chat.smartchat.ai"
            );
            log.info("{} 🔒 Detected production environment → strict CORS mode.", CLASS);
        }

        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        log.info("{} ✅ CORS configured. Allowed origins: {}", CLASS, allowedOrigins);
    }*/

    /**
     * Detects if running inside a Docker container.
     */
    private boolean isRunningInDocker() {
        try {
            return new java.io.File("/.dockerenv").exists() ||
                    System.getenv("DOCKER_CONTAINER") != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Detects if running on a local machine or localhost network.
     */
    private boolean isLocalEnvironment() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String hostAddr = InetAddress.getLocalHost().getHostAddress();
            return hostname.contains("localhost") ||
                    hostAddr.startsWith("127.") ||
                    hostAddr.startsWith("172.") || // typical Docker bridge network
                    hostAddr.startsWith("192.168.");
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
