/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.discovery.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test-mail")
@RequiredArgsConstructor
public class MailTestController {
    private final JavaMailSender mailSender;

    @GetMapping
    public String sendTest() {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("yourgmail@gmail.com");
            message.setSubject("SmartChat Discovery - Test Mail");
            message.setText("✅ SmartChat Discovery email sending works!");
            mailSender.send(message);
            return "Mail sent successfully";
        } catch (Exception e) {
            e.printStackTrace();
            return "Mail failed: " + e.getMessage();
        }
    }
}
