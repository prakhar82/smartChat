/*
 * GoogleContactService
 *
 * Purpose:
 *   Given a Google OAuth access token, fetch the user's Google Contacts (People API)
 *   and delegate to ContactService.syncContacts
 *
 * Where to call:
 *   - Exposed via GoogleContactController /api/contacts/google/sync
 */

package com.smartchat.backend.service;

import com.smartchat.backend.dto.ContactSyncRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleContactService {

    private final ContactService contactService;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String PEOPLE_API =
            "https://people.googleapis.com/v1/people/me/connections?personFields=names,phoneNumbers";

    /**
     * Fetch Google contacts using the provided Google access token and sync.
     */
    public void fetchAndSync(Long userId, String googleAccessToken) {
        String url = PEOPLE_API + "&access_token=" + googleAccessToken;
        Object response = restTemplate.getForObject(url, Object.class);

        List<String> phoneNumbers = extractPhoneNumbers(response);

        ContactSyncRequest req = new ContactSyncRequest();
        req.setUserId(userId);
        req.setContacts(phoneNumbers);
        contactService.syncContacts(req);
    }

    /**
     * Very small placeholder parser. Replace with proper JSON parsing using Jackson or JsonNode.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractPhoneNumbers(Object response) {
        // TODO: robustly parse the People API JSON. For now return empty or hard-coded test numbers.
        return List.of();
    }
}
