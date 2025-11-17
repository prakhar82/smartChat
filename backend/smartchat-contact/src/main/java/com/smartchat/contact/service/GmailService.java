/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class GmailService {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Send an email using Gmail API.
     *
     * @param accessToken Google OAuth access token (Bearer)
     * @param from        sender email (usually "me")
     * @param to          recipient email
     * @param subject     subject line
     * @param body        plain text body
     * @return true if successfully sent
     */
    public boolean sendEmail(String accessToken, String from, String to, String subject, String body) {
        try {
            log.info("[GmailService] Preparing email → to={}, subject={}", to, subject);

            // Build RFC 822 raw message
            String rawMessage = "From: " + from + "\r\n" +
                    "To: " + to + "\r\n" +
                    "Subject: " + subject + "\r\n\r\n" +
                    body;

            // Base64URL encode (Gmail requires URL-safe Base64, no padding)
            String base64UrlEncoded = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(rawMessage.getBytes(StandardCharsets.UTF_8));

            // Request body
            Map<String, String> message = Map.of("raw", base64UrlEncoded);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(message, headers);

            log.debug("[GmailService] Sending POST to Gmail API for recipient {}", to);
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://gmail.googleapis.com/gmail/v1/users/me/messages/send",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();
            if (success) {
                log.info("[GmailService] ✅ Email successfully sent to {}", to);
            } else {
                log.error("[GmailService] ❌ Failed to send email to {} → Status: {}, Body: {}",
                        to, response.getStatusCode(), response.getBody());
            }

            return success;

        } catch (Exception e) {
            log.error("[GmailService] ❌ Exception while sending email to {}: {}", to, e.getMessage(), e);
            return false;
        }
    }
}
