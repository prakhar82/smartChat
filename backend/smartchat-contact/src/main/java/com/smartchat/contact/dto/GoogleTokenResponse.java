/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.dto;

import lombok.Data;

@Data
public class GoogleTokenResponse {
    private String access_token;
    private String refresh_token;
    private Long expires_in;
    private String scope;
    private String token_type;
}
