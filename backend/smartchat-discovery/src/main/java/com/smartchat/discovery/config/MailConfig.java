/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.discovery.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /**
     * Provides a fallback no-op JavaMailSender if no mail configuration is available.
     */
    @Bean
    @ConditionalOnMissingBean
    public JavaMailSender javaMailSender() {
        log.warn("⚠️ No mail configuration detected. Using no-op JavaMailSender.");
        return new JavaMailSenderImpl(); // empty, does nothing
    }
}
