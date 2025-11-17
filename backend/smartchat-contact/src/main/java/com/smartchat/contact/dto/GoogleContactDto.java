/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class GoogleContactDto {
    private String name;
    private List<String> phoneNumbers = new ArrayList<>();
    private List<String> emails = new ArrayList<>();
    private Map<String, Object> raw;
}
