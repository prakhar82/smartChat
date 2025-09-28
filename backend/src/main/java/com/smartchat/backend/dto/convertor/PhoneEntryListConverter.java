/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.dto.convertor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartchat.backend.dto.ContactSyncRequest;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class PhoneEntryListConverter implements AttributeConverter<List<ContactSyncRequest.PhoneEntry>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<ContactSyncRequest.PhoneEntry> attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert phones to JSON", e);
        }
    }

    @Override
    public List<ContactSyncRequest.PhoneEntry> convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData,
                    new TypeReference<List<ContactSyncRequest.PhoneEntry>>() {
                    });
        } catch (Exception e) {
            return List.of();
        }
    }
}
