/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.contracts;

import com.smartchat.common.dto.UserDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ==========================================================
 * 🔐 JwtFeignClient (Feign Client)
 * ----------------------------------------------------------
 * Provides cross-service JWT validation and parsing utilities.
 * Used by other microservices to validate or extract data
 * from JWT tokens issued by the Auth Service.
 * ----------------------------------------------------------
 * ✅ Auto-discovered via Eureka or static URL
 * ✅ Protected by Resilience4j circuit breaker
 * ==========================================================
 */
@FeignClient(
        name = "smartchat-auth",
        url = "${services.auth.url:}",   // optional override for non-Eureka envs
        path = "/auth"               // ✅ removed /api for internal call consistency
)
@CircuitBreaker(name = "jwtServiceCB")
public interface JwtFeignClient {


    @GetMapping("/token")
    ResponseEntity<UserDTO> validateToken(@RequestParam("jwtToken") String token);

    @GetMapping("/extractUsername")
    ResponseEntity<String> extractUsername(@RequestParam("jwtToken") String token);

    @GetMapping("/extractUserId")
    ResponseEntity<Long> extractUserId(@RequestParam("jwtToken") String token);

    @GetMapping("/extractMobile")
    ResponseEntity<String> extractMobile(@RequestParam("jwtToken") String token);
}
