#!/bin/sh
#
# Copyright (c) 2025 SmartChat Contributors
# All rights reserved.
# Unauthorized copying or distribution of this file,
# via any medium, is strictly prohibited unless permitted by license.
# Author: $USER_NAME
#

set -e

echo "[INIT] 🚀 Starting RabbitMQ with auto-cleanup policy..."

# Start RabbitMQ in the background
rabbitmq-server -detached

# Wait for RabbitMQ to become healthy
echo "[INIT] ⏳ Waiting for RabbitMQ startup..."
while ! rabbitmq-diagnostics ping > /dev/null 2>&1; do
  echo "[INIT] ⏳ Waiting for RabbitMQ startup..."
  sleep 3
done

echo "[INIT] ✅ RabbitMQ is ready — applying default policies..."

# Optional: enable automatic mirroring / HA
rabbitmqctl set_policy ha-all ".*" '{"ha-mode":"all"}'

# Optional: enable automatic TTL cleanup for old queues
rabbitmqctl set_policy expire-queues ".*" '{"expires":1800000}' --apply-to queues

echo "[INIT] 🟢 Launching RabbitMQ in foreground..."
exec rabbitmq-server
