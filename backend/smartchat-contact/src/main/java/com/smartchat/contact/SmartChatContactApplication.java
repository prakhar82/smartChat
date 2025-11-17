/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 🚀 SmartChatContactApplication
 * ---------------------------------------------------------------------
 * Contact service:
 * - PostgreSQL (JPA) for structured data
 * - MongoDB for Google token metadata
 * - Redis for cached OAuth tokens
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.smartchat.contact",
                "com.smartchat.common"
        },
        exclude = {
                org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.security.reactive.ReactiveManagementWebSecurityAutoConfiguration.class
        }
)
@ConfigurationPropertiesScan(basePackages = {
        "com.smartchat.contact",
        "com.smartchat.common"
})
@EnableCaching
@EnableAsync
@EnableScheduling

// ✅ Correct store-level repository mappings
@EnableJpaRepositories(basePackages = {
        "com.smartchat.contact.repository"     // ContactRepository (Postgres)
})
@EnableMongoRepositories(basePackages = {
        "com.smartchat.contact.repository"     // GoogleOAuthTokenRepository (Mongo)
})
@EnableRedisRepositories(basePackages = {
        "com.smartchat.contact.repository.cache" // CachedOAuthRepository (Redis)
})

@EnableFeignClients(basePackages = "com.smartchat.common.contracts")
public class SmartChatContactApplication {

    private static final Logger log = LoggerFactory.getLogger(SmartChatContactApplication.class);

    @PostConstruct
    public void init() {
        log.info("📇 SmartChat Contact Service started successfully — JPA + Mongo + Redis configured.");
    }

    public static void main(String[] args) {
        SpringApplication.run(SmartChatContactApplication.class, args);
    }
}
