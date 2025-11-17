/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.contracts;

import com.smartchat.common.contracts.fallback.ChatHealthContractFallback;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ==========================================================
 * 💬 ChatHealthContract (Feign Client)
 * ----------------------------------------------------------
 * Used by other modules (like API Gateway or Monitoring)
 * to check the health of the Chat module’s STOMP relay.
 * ----------------------------------------------------------
 * ✅ Auto-discovered via Eureka
 * ✅ Protected by Resilience4j CircuitBreaker
 * ✅ Graceful fallback when Chat is down
 * ==========================================================
 */
@FeignClient(
        name = "smartchat-chat",
        url = "${services.chat.url:}",  // optional override for non-Eureka environments
        path = "/chat",                 // ✅ aligned with ChatController’s @RequestMapping("/chat")
        fallback = ChatHealthContractFallback.class
)
@CircuitBreaker(name = "chatHealthCB")
public interface ChatHealthContract {

    /**
     * Checks STOMP relay or message broker health status.
     *
     * @return "UP" or "DOWN" depending on Chat relay state.
     */
    @GetMapping(value = "/health", produces = "text/plain")
    String getRelayHealth();
}
