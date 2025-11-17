/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.discovery.listener;

import com.netflix.appinfo.InstanceInfo;
import com.smartchat.discovery.service.DiscoveryNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.netflix.eureka.server.event.EurekaInstanceCanceledEvent;
import org.springframework.cloud.netflix.eureka.server.event.EurekaInstanceRegisteredEvent;
import org.springframework.cloud.netflix.eureka.server.event.EurekaServerStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EurekaEventListener {

    private final DiscoveryNotificationService notificationService;

    @EventListener
    public void onInstanceRegistered(EurekaInstanceRegisteredEvent event) {
        InstanceInfo info = event.getInstanceInfo();
        log.info("🟢 Registered: {} [{}]", info.getAppName(), info.getInstanceId());
        notificationService.notifyServiceEvent(info.getAppName(), "REGISTERED", info.getInstanceId());
    }

    @EventListener
    public void onInstanceCanceled(EurekaInstanceCanceledEvent event) {
        log.warn("🔴 DOWN: {} [{}]", event.getAppName(), event.getServerId());
        notificationService.notifyServiceEvent(event.getAppName(), "DOWN", event.getServerId());
    }

    @EventListener
    public void onServerStarted(EurekaServerStartedEvent event) {
        log.info("🚀 Eureka started - notifying admins");
        notificationService.notifyServiceEvent("Eureka-Discovery", "STARTED", "server");
    }
}
