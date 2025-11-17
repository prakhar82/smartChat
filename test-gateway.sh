#!/bin/bash
#
# Copyright (c) 2025 SmartChat Contributors
# All rights reserved.
# Unauthorized copying or distribution of this file,
# via any medium, is strictly prohibited unless permitted by license.
# Author: $USER_NAME
#

set -e

GATEWAY=https://localhost:8443
TOKEN="YOUR_JWT_TOKEN_HERE"

echo "=== 🔍 Testing SmartChat Gateway ==="
echo ""

echo "1️⃣ Testing CORS preflight..."
curl -s -o /dev/null -w "%{http_code}\n" -X OPTIONS "$GATEWAY/api/contacts/matched" \
  -H "Origin: https://app.smartchat.ai" \
  -H "Access-Control-Request-Method: GET"

echo ""
echo "2️⃣ Testing valid JWT request..."
curl -k -i -X GET "$GATEWAY/api/contacts/matched" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json"

echo ""
echo "3️⃣ Testing invalid JWT request..."
curl -k -i -X GET "$GATEWAY/api/contacts/matched" \
  -H "Authorization: Bearer invalidtoken" \
  -H "Accept: application/json"

echo ""
echo "4️⃣ Checking actuator health..."
curl -k -i "$GATEWAY/actuator/health"

echo ""
echo "✅ All tests executed."
