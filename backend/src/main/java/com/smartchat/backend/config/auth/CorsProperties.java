/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.config.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    private List<String> allowedOrigins = Arrays.asList("http://localhost:4200", "http://localhost");
    private List<String> allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS");
    private List<String> allowedHeaders = Arrays.asList("*");
    private boolean allowCredentials = true;

    // Cache preflight response (default: 30 minutes)
    private Duration maxAge = Duration.ofMinutes(30);
}
