/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.dto;

import lombok.Data;

@Data
public class Contect {
    private String phoneNormalized;
    private String contactName;
    private String phoneRaw;
}
