/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.config;

import com.smartchat.backend.websocket.JwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                // 🔧 Allow multiple origins for frontend devs (localhost, Vercel, etc.)
                .setAllowedOriginPatterns("*")
                .withSockJS(); // ✅ SockJS fallback for Angular/STOMP
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 🔧 Standard prefix for controller mapping
        registry.setApplicationDestinationPrefixes("/app");

        // 🔧 Enable simple in-memory broker for all /topic and /queue
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{5000, 5000})  // 🔧 faster heartbeat
                .setTaskScheduler(heartBeatScheduler());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 🔧 Attach custom JWT-based authentication interceptor
        registration.interceptors(jwtChannelInterceptor);
    }

    private ThreadPoolTaskScheduler heartBeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-thread-");
        scheduler.initialize();
        return scheduler;
    }
}
