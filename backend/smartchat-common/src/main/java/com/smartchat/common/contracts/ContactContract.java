/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.contracts;

import com.smartchat.common.contracts.fallback.ContactContractFallback;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ==========================================================
 * 📇 ContactContract (Feign Client)
 * ----------------------------------------------------------
 * Allows other services (Auth, API, Chat) to trigger
 * contact synchronization or Google contact imports
 * in the Contact Service.
 * ----------------------------------------------------------
 * ✅ Auto-discovered via Eureka or static URL
 * ✅ Load-balanced with Spring Cloud
 * ✅ Resilience4j-protected circuit breaker
 * ✅ Graceful fallback via ContactContractFallback
 * ==========================================================
 */
@FeignClient(
        name = "smartchat-contact",
        url = "${services.contact.url:}",  // optional for non-Eureka environments
        path = "/contact",                 // ✅ no longer /api/contacts
        fallback = ContactContractFallback.class
)
@CircuitBreaker(name = "contactServiceCB")
public interface ContactContract {

    /**
     * Triggers contact synchronization for a user, using their Google access token.
     *
     * @param ownerUserId The ID of the user initiating sync
     * @param accessToken OAuth2 access token for Google Contacts API
     */
    @PostMapping(value = "/sync", consumes = "application/x-www-form-urlencoded")
    void fetchAndSync(
            @RequestParam("ownerUserId") Long ownerUserId,
            @RequestParam("accessToken") String accessToken
    );
}
