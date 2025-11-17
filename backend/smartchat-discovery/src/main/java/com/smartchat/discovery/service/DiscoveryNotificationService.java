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
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * ==========================================================
 * 🌐 DiscoveryNotificationService
 * ----------------------------------------------------------
 * Unified alerting for Eureka service lifecycle events:
 * - Email
 * - Slack
 * - Discord
 * ==========================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryNotificationService {

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${alert.mail.enabled:true}")
    private boolean mailEnabled;
    @Value("${alert.mail.from:noreply@smartchat.io}")
    private String from;
    @Value("${alert.mail.to:admin@smartchat.io}")
    private String to;

    @Value("${alert.slack.enabled:false}")
    private boolean slackEnabled;
    @Value("${alert.slack.webhook-url:}")
    private String slackWebhook;

    @Value("${alert.discord.enabled:false}")
    private boolean discordEnabled;
    @Value("${alert.discord.webhook-url:}")
    private String discordWebhook;

    @PostConstruct
    public void init() {
        log.info("🔔 DiscoveryNotificationService active. Mail={}, Slack={}, Discord={}",
                mailEnabled, slackEnabled, discordEnabled);
    }

    @Async
    public void notifyServiceEvent(String serviceName, String status, String instanceId) {
        String message = buildMessage(serviceName, status, instanceId);

        if (mailEnabled) sendMail(serviceName, status, message);
        if (slackEnabled) sendSlack(message);
        if (discordEnabled) sendDiscord(message);
    }

    private String buildMessage(String serviceName, String status, String instanceId) {
        return String.format("""
                📢 *SmartChat Service Alert*
                • *Service:* %s
                • *Status:* %s
                • *Instance ID:* %s
                • *Time:* %s
                """, serviceName, status.toUpperCase(), instanceId, Instant.now());
    }

    private void sendMail(String serviceName, String status, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject("[SmartChat] Service " + serviceName + " is " + status);
            msg.setText(body);
            mailSender.send(msg);
            log.info("📧 Email sent for {} [{}]", serviceName, status);
        } catch (Exception e) {
            log.error("❌ Failed to send email: {}", e.getMessage());
        }
    }

    private void sendSlack(String message) {
        try {
            restTemplate.postForEntity(slackWebhook, Map.of("text", message), Void.class);
            log.info("💬 Slack alert sent");
        } catch (Exception e) {
            log.error("❌ Failed to send Slack alert: {}", e.getMessage());
        }
    }

    private void sendDiscord(String message) {
        try {
            restTemplate.postForEntity(discordWebhook, Map.of("content", message), Void.class);
            log.info("🎮 Discord alert sent");
        } catch (Exception e) {
            log.error("❌ Failed to send Discord alert: {}", e.getMessage());
        }
    }
}
