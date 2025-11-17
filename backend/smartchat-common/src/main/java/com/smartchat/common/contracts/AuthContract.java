/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.contracts;

import com.smartchat.common.contracts.fallback.AuthContractFallback;
import com.smartchat.common.dto.InviteTokenDTO;
import com.smartchat.common.dto.UserDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * ==========================================================
 * 🔐 AuthContract (Feign Client)
 * ----------------------------------------------------------
 * Provides inter-service communication with the Auth Service.
 * Automatically load-balanced via Eureka if available.
 * Includes Resilience4j circuit breaker protection.
 * ==========================================================
 */
@FeignClient(
        name = "smartchat-auth",
        url = "${services.auth.url:}",   // Optional, resolved from Eureka or config
        path = "/auth",                  // ✅ no longer /api/auth
        fallback = AuthContractFallback.class
)
@CircuitBreaker(name = "authServiceCB")
public interface AuthContract {

    // 🔹 Fetch a user by their unique ID
    @GetMapping("/user/{id}")
    UserDTO getUserById(@PathVariable("id") String userId);

    // 🔹 Resolve a user by JWT token
    @GetMapping("/token")
    UserDTO getUserByToken(@RequestParam("jwtToken") String jwtToken);

    // 🔹 Validate invite token for onboarding
    @GetMapping("/invite/validate")
    InviteTokenDTO validateInviteToken(@RequestParam("token") String token);

    // 🔹 Retrieve all users (admin-level)
    @GetMapping("/users")
    List<UserDTO> getAllUsers();

    // 🔹 Fetch user by email
    @GetMapping("/email")
    UserDTO getUserByEmail(@RequestParam("email") String email);

    // 🔹 Fetch user by mobile
    @GetMapping("/mobile")
    UserDTO getUserByMobile(@RequestParam("mobile") String mobile);

    // 🔹 Resolve numeric user ID from username/principal
    @GetMapping("/resolve")
    Long resolveUserIdFromPrincipal(@RequestParam("principal") String principal);
}
