/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

-- V4__create_invite_tokens_table.sql
-- Migration: Create invite_tokens table
-- Author: $USER_NAME
-- Date: 2025-09-29

CREATE TABLE invite_tokens
(
    id          BIGSERIAL PRIMARY KEY,

    token       VARCHAR(64) NOT NULL UNIQUE,

    inviter_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMP,
    used        BOOLEAN     NOT NULL DEFAULT FALSE
);

-- Index on token (already enforced as UNIQUE but faster lookup too)
CREATE INDEX idx_invite_token ON invite_tokens (token);

-- Index on inviter for quick lookups
CREATE INDEX idx_invite_tokens_inviter ON invite_tokens (inviter_id);

-- Optional: index on used flag (helps when fetching unused invites)
CREATE INDEX idx_invite_tokens_used ON invite_tokens (used);
