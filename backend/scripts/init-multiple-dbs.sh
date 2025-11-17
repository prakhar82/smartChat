#!/bin/bash
#
# Copyright (c) 2025 SmartChat Contributors
# All rights reserved.
# Unauthorized copying or distribution of this file,
# via any medium, is strictly prohibited unless permitted by license.
# Author: $USER_NAME
#

#
# SmartChat :: Multi-Database Initializer for PostgreSQL
# Creates multiple databases (for auth & contact services)
# using environment variable POSTGRES_MULTIPLE_DATABASES
#

set -e

# --- Split the comma-separated database list into an array ---
if [ -z "$POSTGRES_MULTIPLE_DATABASES" ]; then
  echo "❌ No databases specified in POSTGRES_MULTIPLE_DATABASES"
  exit 0
fi

echo "🧱 Creating multiple databases: $POSTGRES_MULTIPLE_DATABASES"

# --- Connect as the main Postgres user ---
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
EOSQL

# --- Loop through and create each database ---
for db in $(echo $POSTGRES_MULTIPLE_DATABASES | tr ',' ' ')
do
  echo "➡️  Creating database: '$db'"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
      CREATE DATABASE $db;
EOSQL
done

echo "✅ All databases created successfully."
