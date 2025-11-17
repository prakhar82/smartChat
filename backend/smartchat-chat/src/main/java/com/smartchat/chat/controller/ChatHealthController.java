/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.controller;

import com.smartchat.chat.contracts.ChatHealthContractImpl;
import com.smartchat.chat.websocket.StompRelayHealthIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ==========================================================
 * 🩺 ChatHealthController
 * ----------------------------------------------------------
 * REST endpoints exposing Chat service health for Feign clients,
 * orchestrators, and observability dashboards.
 * <p>
 * Endpoints:
 * - GET /chat/health       → basic relay status ("UP" | "DOWN")
 * - GET /chat/health/full  → full subsystem diagnostics
 * ==========================================================
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatHealthController {

    private final ChatHealthContractImpl chatHealthContractImpl;
    private final StompRelayHealthIndicator stompRelayHealthIndicator;
    private final RedisConnectionFactory redisConnectionFactory;
    private final MongoTemplate mongoTemplate;

    // ==========================================================
    // ⚙️ Basic Health (Relay Only)
    // ==========================================================
    @GetMapping("/health")
    public ResponseEntity<String> getRelayHealth() {
        String status = chatHealthContractImpl.getRelayHealth();
        log.debug("[ChatHealthController] STOMP relay reported → {}", status);
        return ResponseEntity.ok(status);
    }

    // ==========================================================
    // 🧩 Full Subsystem Health (Detailed)
    // ==========================================================
    @GetMapping("/health/full")
    public ResponseEntity<Map<String, Object>> getFullHealth() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());

        // STOMP relay
        result.put("stompRelay", checkStompRelay());

        // Redis
        result.put("redis", checkRedis());

        // RabbitMQ
        result.put("rabbitMQ", checkRabbitMQ("rabbitmq", 5672));

        // MongoDB
        result.put("mongoDB", checkMongo());

        // Aggregate overall
        boolean overall = result.values().stream()
                .filter(Map.class::isInstance)
                .map(v -> (Map<?, ?>) v)
                .allMatch(map -> Boolean.TRUE.equals(map.get("healthy")));

        result.put("overallStatus", overall ? "UP" : "DOWN");

        log.info("[ChatHealthController] 🩺 Full health report: {}", result);
        return ResponseEntity.ok(result);
    }

    // ==========================================================
    // 🩸 Individual Health Checks
    // ==========================================================
    private Map<String, Object> checkStompRelay() {
        Map<String, Object> map = new HashMap<>();
        long start = System.currentTimeMillis();
        boolean healthy = false;
        try {
            healthy = stompRelayHealthIndicator.isRelayAvailable();
        } catch (Exception e) {
            log.debug("[ChatHealthController] STOMP relay check failed: {}", e.getMessage());
        }
        map.put("healthy", healthy);
        map.put("latencyMs", System.currentTimeMillis() - start);
        return map;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> map = new HashMap<>();
        long start = System.currentTimeMillis();
        boolean healthy = false;

        try (RedisConnection conn = redisConnectionFactory.getConnection()) {
            String pong = conn.ping();
            healthy = pong != null && pong.equalsIgnoreCase("PONG");
        } catch (Exception e) {
            log.debug("[ChatHealthController] Redis check failed: {}", e.getMessage());
        }

        map.put("healthy", healthy);
        map.put("latencyMs", System.currentTimeMillis() - start);
        return map;
    }

    private Map<String, Object> checkRabbitMQ(String host, int port) {
        Map<String, Object> map = new HashMap<>();
        long start = System.currentTimeMillis();
        boolean healthy = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            healthy = true;
        } catch (Exception e) {
            log.debug("[ChatHealthController] RabbitMQ check failed: {}", e.getMessage());
        }
        map.put("healthy", healthy);
        map.put("latencyMs", System.currentTimeMillis() - start);
        return map;
    }

    private Map<String, Object> checkMongo() {
        Map<String, Object> map = new HashMap<>();
        long start = System.currentTimeMillis();
        boolean healthy = false;
        try {
            mongoTemplate.executeCommand(new Document("ping", 1));
            healthy = true;
        } catch (Exception e) {
            log.debug("[ChatHealthController] MongoDB check failed: {}", e.getMessage());
        }
        map.put("healthy", healthy);
        map.put("latencyMs", System.currentTimeMillis() - start);
        return map;
    }
}
