/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@SpringBootApplication(scanBasePackages = "com.smartchat.backend")
@EnableJpaRepositories(basePackages = "com.smartchat.backend.repository.jpa")
@EnableMongoRepositories(basePackages = "com.smartchat.backend.repository.mongo")
@EnableRedisRepositories(basePackages = "com.smartchat.backend.repository.redis")
public class SmartChatBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartChatBackendApplication.class, args);
    }
}
