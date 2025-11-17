/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smartchat.chat.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * =============================================================
 * 🧠 RedisConfig — Unified Redis Serialization Setup
 * -------------------------------------------------------------
 * Provides type-safe RedisTemplate beans for:
 * • ChatMessage (chat persistence)
 * • Generic object caching (contacts, sessions, etc.)
 * <p>
 * ✅ JSON-based serialization (no Java serialization)
 * ✅ Custom ObjectMapper (independent from Spring MVC)
 * ✅ Safe for clustered and multi-node deployments
 * =============================================================
 */
@Slf4j
@Configuration
public class RedisConfig {

    /**
     * =========================================================
     * 💬 RedisTemplate for ChatMessage (chat history, delivery)
     * =========================================================
     */
    @Bean
    public RedisTemplate<String, ChatMessage> chatMessageRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, ChatMessage> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        log.info("[RedisConfig] ✅ RedisTemplate<String, ChatMessage> initialized successfully");
        return template;
    }

    /**
     * =========================================================
     * 🧩 Generic RedisTemplate for general caching
     * =========================================================
     */
    @Primary
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        log.info("[RedisConfig] ✅ Generic RedisTemplate<String, Object> initialized successfully");
        return template;
    }

    /**
     * =========================================================
     * 🧠 Dedicated ObjectMapper for Redis JSON serialization
     * ---------------------------------------------------------
     * 🚫 Does NOT affect Spring Boot’s MVC ObjectMapper
     * =========================================================
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return objectMapper;
    }
}
