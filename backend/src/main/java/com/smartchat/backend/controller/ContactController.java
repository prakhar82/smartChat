/*
 * ContactController
 *
 * Purpose:
 *  - /api/contacts/sync  → accept device & Google contacts and persist for matching
 *  - /api/contacts/matched?userId=... → return matched SmartChat users
 *
 * Called from:
 *  - Angular/Capacitor frontend after login/register
 */

package com.smartchat.backend.controller;

import com.smartchat.backend.dto.ContactSyncRequest;
import com.smartchat.backend.dto.MatchedContactResponse;
import com.smartchat.backend.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @PostMapping("/sync")
    public void syncContacts(@RequestBody ContactSyncRequest request) {
        contactService.syncContacts(request);
    }

    @GetMapping("/matched")
    public List<MatchedContactResponse> getMatchedContacts(@RequestParam Long userId) {
        return contactService.getMatchedContacts(userId);
    }
}
