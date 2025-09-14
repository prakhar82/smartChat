/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.apache.commons.codec.binary.Base64;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.Properties;

/**
 * GmailService - sends email using a user's Google OAuth access token.
 */
@Service
public class GmailService {

    private final WebClient webClient = WebClient.builder().build();

    /**
     * Send an email as the authenticated user using Gmail API.
     *
     * @param ownerAccessToken OAuth2 access token for the owner (inviter)
     * @param fromEmail sender email (the inviter)
     * @param toEmail recipient email
     * @param subject subject
     * @param body plain text body
     * @return true if sent
     */
    public boolean sendEmail(String ownerAccessToken, String fromEmail, String toEmail, String subject, String body) {
        try {
            // Build MIME message
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);
            MimeMessage email = new MimeMessage(session);
            email.setFrom(new InternetAddress(fromEmail));
            email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(toEmail));
            email.setSubject(subject);
            email.setText(body);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            email.writeTo(buffer);
            byte[] rawMessageBytes = buffer.toByteArray();
            String encodedEmail = Base64.encodeBase64URLSafeString(rawMessageBytes);

            String payload = "{\"raw\":\"" + encodedEmail + "\"}";

            // Call Gmail API
            var resp = webClient.post()
                    .uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerAccessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            if (resp != null && resp.getStatusCode().is2xxSuccessful()) {
                return true;
            } else {
                return false;
            }
        } catch (Exception ex) {
            System.err.println("[GmailService] sendEmail failed: " + ex.getMessage());
            return false;
        }
    }
}
