/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.api.controller;

import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
public class GatewayRoutesController {

    private final RouteDefinitionLocator locator;

    public GatewayRoutesController(RouteDefinitionLocator locator) {
        this.locator = locator;
    }

    @GetMapping("/actuator/gateway/routes")
    public Flux<Map<String, Object>> routes() {
        return locator.getRouteDefinitions()
                .map(route -> Map.of(
                        "id", route.getId(),
                        "uri", route.getUri().toString(),
                        "predicates", route.getPredicates(),
                        "filters", route.getFilters()
                ));
    }
}
