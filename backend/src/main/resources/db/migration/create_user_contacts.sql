/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

CREATE TABLE IF NOT EXISTS user_contacts (
                                             id BIGSERIAL PRIMARY KEY,
                                             owner_user_id BIGINT NOT NULL,
                                             contact_name TEXT,
                                             phone_normalized VARCHAR(50),
    phone_raw VARCHAR(100),
    matched_user_id BIGINT,
    source VARCHAR(30),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_user_contacts_owner_phone ON user_contacts(owner_user_id, phone_normalized);
