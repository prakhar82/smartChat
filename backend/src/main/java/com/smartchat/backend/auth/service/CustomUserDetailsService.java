/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.auth.service;

import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

/**
 * ==========================================================
 * ✅ CustomUserDetailsService
 * ----------------------------------------------------------
 * Loads user data for authentication based on either:
 * - Mobile number (default), or
 * - Email address (for Google / JWT tokens using email)
 * ==========================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        log.debug("[CustomUserDetailsService] Loading user by mobileNumber/email={}", identifier);

        // 🔍 Try finding by mobile first, then email if it looks like one
        Optional<User> userOpt = userRepository.findByMobileNumber(identifier);

        if (userOpt.isEmpty() && identifier.contains("@")) {
            log.debug("[CustomUserDetailsService] No match by mobile → trying email lookup for {}", identifier);
            userOpt = userRepository.findByEmail(identifier);
        }

        User user = userOpt.orElseThrow(() -> {
            log.error("[CustomUserDetailsService] User not found for identifier={}", identifier);
            return new UsernameNotFoundException("User not found");
        });

        // 🛡️ Ensure Spring role format (prefix "ROLE_")
        String roleName = user.getRole();
        if (roleName == null || roleName.isBlank()) {
            roleName = "ROLE_USER";
        } else if (!roleName.startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName;
        }

        GrantedAuthority authority = new SimpleGrantedAuthority(roleName);
        log.info("[CustomUserDetailsService] ✅ Loaded userId={} ({}) with role={}",
                user.getId(), user.getMobileNumber(), authority.getAuthority());

        // 👤 Return Spring Security principal
        return new org.springframework.security.core.userdetails.User(
                user.getMobileNumber(), // principal still mobile number internally
                user.getPassword(),
                Collections.singleton(authority)
        );
    }
}
