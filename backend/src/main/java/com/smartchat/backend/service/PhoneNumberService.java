/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PhoneNumberService {

    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    @Value("${contacts.fallbackRegions:US,IN,GB}")
    private String fallbackRegionsRaw;

    public String normalizeToE164(String raw, String ownerMobileE164) {
        if (raw == null) return null;
        String cleaned = raw.trim();
        try {
            if (cleaned.startsWith("+")) {
                Phonenumber.PhoneNumber pn = phoneUtil.parse(cleaned, null);
                if (phoneUtil.isValidNumber(pn)) {
                    return phoneUtil.format(pn, PhoneNumberUtil.PhoneNumberFormat.E164);
                } else {
                    return null;
                }
            }

            List<String> regionsToTry = new ArrayList<>();
            if (ownerMobileE164 != null && !ownerMobileE164.isBlank()) {
                try {
                    Phonenumber.PhoneNumber owner = phoneUtil.parse(ownerMobileE164, null);
                    String ownerRegion = phoneUtil.getRegionCodeForNumber(owner);
                    if (ownerRegion != null && !ownerRegion.isBlank()) regionsToTry.add(ownerRegion);
                } catch (NumberParseException ignored) { }
            }

            for (String r : fallbackRegionsRaw.split(",")) {
                String rr = r.trim();
                if (!rr.isEmpty() && !regionsToTry.contains(rr)) regionsToTry.add(rr);
            }

            for (String region : regionsToTry) {
                try {
                    Phonenumber.PhoneNumber pn = phoneUtil.parse(cleaned, region);
                    if (phoneUtil.isValidNumber(pn)) {
                        return phoneUtil.format(pn, PhoneNumberUtil.PhoneNumberFormat.E164);
                    }
                } catch (NumberParseException ignored) { }
            }

            String digits = cleaned.replaceAll("[^0-9]", "");
            if (digits.length() >= 7) {
                for (String region : regionsToTry) {
                    try {
                        Phonenumber.PhoneNumber pn = phoneUtil.parse(digits, region);
                        if (phoneUtil.isValidNumber(pn)) {
                            return phoneUtil.format(pn, PhoneNumberUtil.PhoneNumberFormat.E164);
                        }
                    } catch (NumberParseException ignored) { }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public Optional<String> inferRegionFromE164(String e164) {
        if (e164 == null) return Optional.empty();
        try {
            Phonenumber.PhoneNumber pn = phoneUtil.parse(e164, null);
            String region = phoneUtil.getRegionCodeForNumber(pn);
            return Optional.ofNullable(region);
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }
}
