/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.auth.service;

import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String mobileNumber) throws UsernameNotFoundException {
        log.debug("[CustomUserDetailsService] Loading user by mobileNumber={}", mobileNumber);

        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> {
                    log.error("[CustomUserDetailsService] User not found for mobileNumber={}", mobileNumber);
                    return new UsernameNotFoundException("User not found");
                });

        GrantedAuthority authority = new SimpleGrantedAuthority(
                user.getRole() != null ? user.getRole() : "ROLE_USER"
        );

        log.info("[CustomUserDetailsService] Loaded userId={} with role={}", user.getId(), authority.getAuthority());

        return new org.springframework.security.core.userdetails.User(
                user.getMobileNumber(),
                user.getPassword(),
                Collections.singleton(authority)
        );
    }
}
