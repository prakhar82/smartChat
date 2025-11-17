/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ============================================================
 * 🐇 RabbitConfig (Final Clean Version)
 * ------------------------------------------------------------
 * This configuration supports Spring's STOMP broker relay:
 * <p>
 * convertAndSendToUser(userId, "/queue/presence")
 * <p>
 * The STOMP broker automatically maps this to RabbitMQ routing key:
 * <p>
 * user.{userId}.queue.presence
 * <p>
 * Therefore, WE MUST create a queue with the exact same name.
 * <p>
 * This version:
 * ✔ creates ONLY the presence queue used by frontend & backend
 * ✔ uses durable queues
 * ✔ uses direct exchange smartchat.direct
 * ✔ avoids generating invalid routing keys that break STOMP
 * ============================================================
 */
@Configuration
@RequiredArgsConstructor
public class RabbitConfig {

    private final ConnectionFactory connectionFactory;

    @Bean
    public RabbitAdmin rabbitAdmin() {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public DirectExchange wsExchange() {
        return new DirectExchange("smartchat.direct", true, false);
    }

    /**
     * Declare queue used for:
     * /user/{id}/queue/presence
     * <p>
     * STOMP broker relay creates routing key:
     * user.{id}.queue.presence
     * <p>
     * >>> DO NOT MODIFY THIS ROUTING KEY <<<
     * >>> DO NOT ADD EXTRA QUEUES <<<
     */
    public void declareUserQueues(String userId) {

        String routingKey = "user." + userId + ".queue.presence";

        RabbitAdmin admin = rabbitAdmin();

        // durable, non-exclusive, non-auto-delete
        Queue queue = new Queue(routingKey, true, false, false);

        admin.declareQueue(queue);

        admin.declareBinding(
                BindingBuilder.bind(queue)
                        .to(wsExchange())
                        .with(routingKey)
        );
    }
}
