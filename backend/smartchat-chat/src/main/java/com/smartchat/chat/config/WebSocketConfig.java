/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.config;

import com.smartchat.chat.websocket.JwtChannelInterceptor;
import com.smartchat.chat.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * ============================================================
 * ✅ WebSocketConfig (Final)
 * ------------------------------------------------------------
 * - Single STOMP endpoint: /ws-chat (SockJS)
 * - Secured via JwtChannelInterceptor
 * - Broker relay: RabbitMQ
 * - /user/** routing enabled
 * ============================================================
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Value("${messaging.stomp.relay.host:rabbitmq}")
    private String relayHost;

    @Value("${messaging.stomp.relay.port:61613}")
    private int relayPort;

    @Value("${messaging.stomp.relay.login:smartchat_user}")
    private String relayUsername;

    @Value("${messaging.stomp.relay.passcode:smartchat123}")
    private String relayPassword;

    @Value("${messaging.stomp.relay.virtual-host:smartchat}")
    private String relayVirtualHost;

    /* ============================================================
     * 1️⃣ STOMP Endpoint (Single, SockJS-enabled)
     * ============================================================
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .addInterceptors(jwtHandshakeInterceptor)  // 🩵 attach here
                .setAllowedOriginPatterns("*")
                .withSockJS();

        log.info("[WebSocketConfig] ✅ Registered SockJS endpoint /ws-chat (with JWT handshake)");
    }

    /* ============================================================
     * 2️⃣ Inbound Channel Interceptor (JWT)
     * ============================================================
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
        log.info("[WebSocketConfig] ✅ JwtChannelInterceptor applied to inbound channel");
    }

    /* ============================================================
     * 3️⃣ RabbitMQ STOMP Relay Configuration
     * ============================================================
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // Prefix for application-level destinations (/app/**)
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific queues (/user/queue/**)
        registry.setUserDestinationPrefix("/user");

        // Relay configuration for RabbitMQ STOMP plugin
        registry.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                .setClientLogin(relayUsername)
                .setClientPasscode(relayPassword)
                .setSystemLogin(relayUsername)
                .setSystemPasscode(relayPassword)
                .setVirtualHost(relayVirtualHost)
                .setSystemHeartbeatSendInterval(10000)
                .setSystemHeartbeatReceiveInterval(10000);

        log.info("[WebSocketConfig] ✅ STOMP broker relay configured -> host={} port={} vhost={}",
                relayHost, relayPort, relayVirtualHost);
    }
}
