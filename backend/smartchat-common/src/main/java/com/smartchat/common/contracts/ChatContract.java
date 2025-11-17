/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.contracts;

import com.smartchat.common.contracts.fallback.ChatContractFallback;
import com.smartchat.common.dto.MatchedContactResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * ==========================================================
 * 💬 ChatContract (Feign Client)
 * ----------------------------------------------------------
 * Provides communication with the SmartChat Chat microservice.
 * Automatically load-balanced via Eureka (no hardcoded URLs).
 * Includes Resilience4j Circuit Breaker protection.
 * ==========================================================
 */
@FeignClient(
        name = "smartchat-chat",
        url = "${services.chat.url:}",  // Optional override for non-Eureka environments
        path = "/chat",                 // ✅ aligned with ChatController’s @RequestMapping("/chat")
        fallback = ChatContractFallback.class
)
@CircuitBreaker(name = "chatServiceCB")
public interface ChatContract {

    /**
     * Retrieves recently matched chat contacts for a given user.
     *
     * @param userId ID of the user whose matched contacts to fetch
     * @return List of matched contact responses
     */
    @GetMapping("/recent")
    List<MatchedContactResponse> findMatchedContacts(@RequestParam("userId") String userId);
}
