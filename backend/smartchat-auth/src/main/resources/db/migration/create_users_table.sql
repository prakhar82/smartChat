/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

-- V0__create_users_table.sql
-- Migration: Create users table
-- Author: $USER_NAME
-- Date: 2025-09-29

CREATE TABLE users
(
    id                BIGSERIAL PRIMARY KEY,

    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,

    email             VARCHAR(255) NOT NULL UNIQUE,

    country_code      VARCHAR(10)  NOT NULL,
    mobile_number     VARCHAR(20)  NOT NULL,
    mobile_normalized VARCHAR(30)  NOT NULL UNIQUE,

    password          VARCHAR(255) NOT NULL,
    role              VARCHAR(50)  NOT NULL,

    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),

    referred_by_id    BIGINT       REFERENCES users (id) ON DELETE SET NULL
);

-- Useful indexes
CREATE INDEX idx_users_mobile_normalized ON users (mobile_normalized);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_referred_by ON users (referred_by_id);
