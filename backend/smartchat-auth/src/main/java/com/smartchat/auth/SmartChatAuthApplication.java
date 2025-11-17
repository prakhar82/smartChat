/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.auth;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 🔐 SmartChat Auth Service
 * Handles authentication, JWT, and registration logic.
 */
@SpringBootApplication(scanBasePackages = {
        "com.smartchat.auth",
        "com.smartchat.common"
})
@ConfigurationPropertiesScan(basePackages = {
        "com.smartchat.auth",
        "com.smartchat.common"
})
@EnableAsync
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.smartchat.common.contracts")

// Enable both JPA & Redis Repositories
@EnableJpaRepositories(basePackages = "com.smartchat.auth.repository")
@EnableRedisRepositories(basePackages = "com.smartchat.common.cache.repository")
public class SmartChatAuthApplication {

    private static final Logger log = LoggerFactory.getLogger(SmartChatAuthApplication.class);

    @PostConstruct
    void init() {
        log.info("✅ SmartChat Auth Service started successfully!");
    }

    public static void main(String[] args) {
        SpringApplication.run(SmartChatAuthApplication.class, args);
    }
}
