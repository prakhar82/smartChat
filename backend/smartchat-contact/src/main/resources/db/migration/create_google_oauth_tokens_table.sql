/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

-- V3__create_google_oauth_tokens_table.sql
-- Migration: Create google_oauth_tokens table
-- Author: $USER_NAME
-- Date: 2025-09-29

CREATE TABLE google_oauth_tokens
(
    id            BIGSERIAL PRIMARY KEY,

    owner_user_id BIGINT NOT NULL,

    access_token  TEXT,
    refresh_token TEXT,
    token_type    VARCHAR(50),
    scope         VARCHAR(500),

    expires_at    TIMESTAMP,
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_google_oauth_tokens_owner ON google_oauth_tokens (owner_user_id);
CREATE INDEX idx_google_oauth_tokens_expires_at ON google_oauth_tokens (expires_at);
