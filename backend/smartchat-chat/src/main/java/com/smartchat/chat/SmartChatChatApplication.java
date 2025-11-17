/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

/**
 * ==========================================================
 * 🚀 SmartChatChatApplication
 * ----------------------------------------------------------
 * The Chat microservice of SmartChat.
 * Provides:
 * - WebSocket-based real-time messaging
 * - Redis pub/sub synchronization
 * - MongoDB persistence for chat history
 * <p>
 * ==========================================================
 * ⚙️ Why this configuration exists
 * ----------------------------------------------------------
 * ✅ 1. `scanBasePackages`
 * Ensures both Chat-specific beans and shared components from
 * `smartchat-common` are scanned. The common module includes
 * reusable DTOs, Feign clients, and utility classes.
 * <p>
 * ✅ 2. `@EnableFeignClients(basePackages = "com.smartchat.common.contracts")`
 * Registers Feign clients defined in the `common` module
 * (e.g., `JwtFeignClient`, `AuthContract`) so that this service
 * can securely communicate with the Auth microservice.
 * <p>
 * Without this, the Chat service would fail to inject
 * `JwtFeignClient`, causing:
 * → No qualifying bean of type 'JwtFeignClient' available.
 * <p>
 * ✅ 3. `@Import(FeignAutoConfiguration.class)`
 * Explicitly imports Feign's auto-configuration.
 * Spring Boot 3.4+ no longer guarantees automatic inclusion
 * of Feign configuration when used across modular JARs.
 * <p>
 * This ensures Feign context initialization even when
 * `JwtFeignClient` resides in another module (common).
 * <p>
 * ✅ 4. `@ConfigurationPropertiesScan`
 * Enables binding of configuration properties defined under
 * both `chat` and `common` modules (e.g., Redis, STOMP relay).
 * <p>
 * ✅ 5. MongoDB + Redis Enablers
 * - `@EnableMongoRepositories` and `@EnableMongoAuditing`
 * allow chat persistence in MongoDB.
 * - `@EnableRedisRepositories` enables caching and pub/sub.
 * <p>
 * ✅ 6. Async & Scheduling
 * - `@EnableAsync`: Enables parallel tasks such as message delivery.
 * - `@EnableScheduling`: Supports cron-based maintenance jobs.
 * <p>
 * ✅ 7. WebSocket Broker
 * - `@EnableWebSocketMessageBroker` activates STOMP endpoints
 * for bi-directional real-time communication.
 * <p>
 * ==========================================================
 * 🧠 Summary of the Feign Fix:
 * ----------------------------------------------------------
 * Problem:
 * Chat service failed to start with:
 * "No qualifying bean of type 'JwtFeignClient' available"
 * <p>
 * Root Cause:
 * Feign clients from `common` were not registered in Chat’s
 * application context, and Feign auto-configuration was not
 * imported automatically in Spring Boot 3.4.
 * <p>
 * Solution:
 * ✅ Added `@EnableFeignClients(basePackages = "com.smartchat.common.contracts")`
 * ✅ Added `@Import(FeignAutoConfiguration.class)`
 * ✅ Ensured `smartchat-common` includes Feign dependency
 * <p>
 * Result:
 * → Feign clients (JwtFeignClient, AuthContract, etc.) load successfully.
 * → WebSocketAuthInterceptor can now validate JWTs via Auth service.
 * ==========================================================
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.smartchat.chat",
                "com.smartchat.common"
        },
        exclude = {
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class,
                org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
        }
)
@ConfigurationPropertiesScan(basePackages = {
        "com.smartchat.chat",
        "com.smartchat.common"
})
@EnableAsync
@EnableScheduling
@EnableRedisRepositories(basePackages = "com.smartchat.chat.repository")
@EnableMongoRepositories(basePackages = "com.smartchat.chat.repository")
@EnableMongoAuditing
@EnableWebSocketMessageBroker
@Import(FeignAutoConfiguration.class) // 👈 Ensures Feign clients are properly configured across modules
@EnableFeignClients(basePackages = "com.smartchat.common.contracts")
// 👈 Registers shared Feign clients (JwtFeignClient, AuthContract)
public class SmartChatChatApplication {
    private static final Logger log = LoggerFactory.getLogger(SmartChatChatApplication.class);

    @PostConstruct
    public void init() {
        log.info("💬 SmartChat Chat Service started successfully on WebSocket + Redis stack.");
    }

    public static void main(String[] args) {
        SpringApplication.run(SmartChatChatApplication.class, args);
    }
}
