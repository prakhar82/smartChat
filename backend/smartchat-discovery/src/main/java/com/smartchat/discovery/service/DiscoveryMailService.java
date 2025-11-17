/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.discovery.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * ==========================================================
 * 📬 DiscoveryMailService
 * ----------------------------------------------------------
 * Sends alert emails for Eureka service registration events:
 * - Service DOWN
 * - Service UP (Recovery)
 * - Service Registered
 * ==========================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryMailService {

    private final JavaMailSender mailSender;

    @Value("${alert.mail.from:noreply@smartchat.io}")
    private String from;

    @Value("${alert.mail.to:admin@smartchat.io}")
    private String to;

    @Value("${alert.mail.enabled:true}")
    private boolean mailEnabled;

    @PostConstruct
    public void init() {
        if (mailEnabled) {
            log.info("📧 DiscoveryMailService initialized (alerts enabled) — sending to {}", to);
        } else {
            log.warn("⚠️ DiscoveryMailService initialized but mail alerts are DISABLED.");
        }
    }

    @Async
    public void sendServiceAlert(String serviceName, String status, String instanceId) {
        if (!mailEnabled) {
            log.debug("Email alert skipped — mail alerts disabled");
            return;
        }

        try {
            String subject = "[SmartChat] Service " + serviceName + " is " + status;
            String body = String.format("""
                    SmartChat Service Status Alert

                    📦 Service: %s
                    🆔 Instance: %s
                    📊 Status: %s
                    ⏰ Time: %s

                    This alert was generated automatically by SmartChat Discovery Service.
                    """, serviceName, instanceId, status.toUpperCase(), Instant.now());

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.info("📨 Sent alert: {} [{}]", serviceName, status);
        } catch (Exception e) {
            log.error("❌ Failed to send alert for {} [{}]: {}", serviceName, status, e.getMessage());
        }
    }
}
