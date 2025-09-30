/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

-- V2__create_chat_messages_table.sql
-- Migration: Create chat_messages table
-- Author: $USER_NAME
-- Date: 2025-09-29

CREATE TABLE chat_messages
(
    id          BIGSERIAL PRIMARY KEY,

    sender_id   BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,

    message     TEXT,
    emoji       VARCHAR(50),
    file_url    VARCHAR(500),
    file_name   VARCHAR(255),

    status      VARCHAR(20) DEFAULT 'SENT', -- SENT / DELIVERED / READ
    timestamp   TIMESTAMP   DEFAULT NOW()
);

-- Useful indexes for performance
CREATE INDEX idx_chat_messages_sender ON chat_messages (sender_id);
CREATE INDEX idx_chat_messages_receiver ON chat_messages (receiver_id);
CREATE INDEX idx_chat_messages_status ON chat_messages (status);
CREATE INDEX idx_chat_messages_timestamp ON chat_messages (timestamp);
