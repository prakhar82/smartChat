/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartchat.backend.service.GoogleContactService.GoogleContactDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleApiClient {

    private final ObjectMapper mapper;
    //private final WebClient webClient = WebClient.builder().build();
    private final WebClient.Builder webClientBuilder;


    private static final String BASE_URL = "https://people.googleapis.com/v1/people/me/connections";
    private static final String DEFAULT_FIELDS = "names,phoneNumbers,emailAddresses";

    /**
     * 🔍 Fetch contacts from Google People API using the provided access token.
     */
    public List<GoogleContactDto> fetchContacts(String accessToken, int pageSize) {
        List<GoogleContactDto> contacts = new ArrayList<>();
        String nextPageToken = null;

        do {
            final String pageParam = nextPageToken != null ? "&pageToken=" + nextPageToken : "";
            final String url = BASE_URL + "?personFields=" + DEFAULT_FIELDS + "&pageSize=" + pageSize + pageParam;

            /*String response = webClient.get()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            resp -> Mono.error(new RuntimeException("Google People API error: " + resp.statusCode())))
                    .bodyToMono(String.class)
                    .block();*/

            String finalNextPageToken = nextPageToken;
            String response = webClientBuilder
                    .baseUrl("https://people.googleapis.com")
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/people/me/connections")
                            .queryParam("personFields", DEFAULT_FIELDS)
                            .queryParam("pageSize", pageSize)
                            .queryParamIfPresent("pageToken", Optional.ofNullable(finalNextPageToken))
                            .build())
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            resp -> Mono.error(new RuntimeException("Google People API error: " + resp.statusCode())))
                    .bodyToMono(String.class)
                    .block();


            try {
                JsonNode root = mapper.readTree(response);
                if (root.has("connections")) {
                    for (JsonNode conn : root.get("connections")) {
                        GoogleContactDto dto = new GoogleContactDto();
                        dto.setRaw(mapper.convertValue(conn, Map.class));

                        dto.setName(conn.path("names").isArray() && conn.path("names").size() > 0
                                ? conn.get("names").get(0).path("displayName").asText(null)
                                : null);

                        if (conn.has("phoneNumbers")) {
                            conn.get("phoneNumbers").forEach(phone -> {
                                String value = phone.path("value").asText(null);
                                if (value != null) {
                                    String type = phone.path("type").asText("mobile");
                                    dto.getPhoneNumbers().add(type + ":" + value);
                                }
                            });
                        }

                        if (conn.has("emailAddresses")) {
                            conn.get("emailAddresses").forEach(email -> {
                                String value = email.path("value").asText(null);
                                if (value != null) {
                                    String type = email.path("type").asText("home");
                                    dto.getEmails().add(type + ":" + value);
                                }
                            });
                        }

                        contacts.add(dto);
                    }
                }
                nextPageToken = root.path("nextPageToken").asText(null);
            } catch (Exception e) {
                log.error("[GoogleApiClient] ❌ Failed to parse People API response: {}", e.getMessage());
                throw new RuntimeException("Failed to parse People API response", e);
            }

        } while (nextPageToken != null);

        log.info("[GoogleApiClient] ✅ Retrieved {} contacts from Google People API", contacts.size());
        return contacts;
    }
}
