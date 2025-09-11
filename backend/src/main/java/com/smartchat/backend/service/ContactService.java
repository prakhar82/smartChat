/*
 * Copyright (c) 2025 SmartChat Contributors
 * Author: Prakhar Dwivedi
 *
 * Purpose:
 *   Manage contact sync from client and match against registered SmartChat users.
 *
 * Where to call:
 *   - Called by ContactController.syncContacts()
 */
package com.smartchat.backend.service;

import com.smartchat.backend.dto.ContactSyncRequest;
import com.smartchat.backend.dto.MatchedContactResponse;
import com.smartchat.backend.model.Contact;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.ContactRepository;
import com.smartchat.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepo;
    private final UserRepository userRepo;

    /**
     * Sync contacts provided by the client.
     * This stores each contact entry for the owner user and maps to registered SmartChat users when possible.
     */
    public void syncContacts(ContactSyncRequest request) {
        // Basic approach: store contact record per phone; if number maps to a user, save their ID
        List<String> numbers = request.getContacts();
        if (numbers == null || numbers.isEmpty()) return;

        numbers.forEach(phoneRaw -> {
            String phone = normalizePhoneNumber(phoneRaw);
            Contact c = Contact.builder()
                    .ownerUserId(request.getUserId())
                    .phoneNumber(phone)
                    .smartChatUserId(findSmartChatUserId(phone))
                    .build();
            contactRepo.save(c);
        });
    }


    /**
     * Returns only contacts that are matched to SmartChat users, with basic user info.
     */
    public List<MatchedContactResponse> getMatchedContacts(Long userId) {
        return contactRepo.findByOwnerUserId(userId).stream()
                .filter(c -> c.getSmartChatUserId() != null)
                .map(c -> {
                    User u = userRepo.findById(c.getSmartChatUserId()).orElse(null);
                    if (u == null) return null;
                    MatchedContactResponse r = new MatchedContactResponse();
                    r.setUserId(u.getId());
                    r.setPhoneNumber(c.getPhoneNumber());
                    r.setName(u.getName());
                    return r;
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    private Long findSmartChatUserId(String phoneNumber) {
        return userRepo.findByMobileNumber(phoneNumber)
                .map(User::getId)
                .orElse(null);
    }


    private String normalizePhoneNumber(String raw) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil util = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber pn = util.parse(raw, "IN"); // default region IN, adjust as needed
            return util.format(pn, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (Exception e) {
            // fallback to raw trimmed
            return raw == null ? null : raw.trim();
        }
    }

}
