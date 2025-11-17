/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 💬 StompRelayProperties
 * ----------------------------------------------------------
 * Maps messaging.stomp.relay.* configuration keys from YAML.
 */
@Data
@Component
@ConfigurationProperties(prefix = "messaging.stomp.relay")
public class StompRelayProperties {
    private String host;
    private int port;
    private String login;
    private String passcode;
    private String systemLogin;
    private String systemPasscode;
    private String virtualHost;
    private int heartbeatSendInterval;
    private int heartbeatReceiveInterval;
}
