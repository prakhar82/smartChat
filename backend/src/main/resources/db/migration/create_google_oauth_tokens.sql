/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

CREATE TABLE IF NOT EXISTS google_oauth_tokens (
                                                   id BIGSERIAL PRIMARY KEY,
                                                   owner_user_id BIGINT UNIQUE NOT NULL,
                                                   access_token TEXT,
                                                   refresh_token TEXT,
                                                   token_type VARCHAR(50),
    expires_at BIGINT,
    scope TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_google_oauth_owner ON google_oauth_tokens(owner_user_id);