/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

-- V1__create_contacts_table.sql
-- Migration: Create contacts table
-- Author: $USER_NAME
-- Date: 2025-09-29

CREATE TABLE contacts (
                          id BIGSERIAL PRIMARY KEY,
                          owner_user_id BIGINT NOT NULL,
                          contact_name VARCHAR(255),

    -- Flattened columns for uniqueness
                          normalizedPhone VARCHAR(50),
                          normalizedEmail VARCHAR(255),

    -- JSONB columns
                          phones JSONB,
                          emails JSONB,

                          smart_chat_user_id BIGINT,

    -- Audit (optional if you want tracking)
                          created_at TIMESTAMP DEFAULT NOW(),
                          updated_at TIMESTAMP DEFAULT NOW()
);

-- Add uniqueness constraints
ALTER TABLE contacts
    ADD CONSTRAINT uq_contacts_owner_phone UNIQUE (owner_user_id, normalizedPhone);

ALTER TABLE contacts
    ADD CONSTRAINT uq_contacts_owner_email UNIQUE (owner_user_id, normalizedEmail);

-- Optional: Indexes for faster search
CREATE INDEX idx_contacts_owner ON contacts(owner_user_id);
CREATE INDEX idx_contacts_phone ON contacts(normalizedPhone);
CREATE INDEX idx_contacts_email ON contacts(normalizedEmail);
CREATE INDEX idx_contacts_phones_jsonb ON contacts USING GIN (phones);
CREATE INDEX idx_contacts_emails_jsonb ON contacts USING GIN (emails);
