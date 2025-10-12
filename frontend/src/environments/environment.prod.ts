/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

export const environment = {
  production: true,

  // ✅ Use relative paths — Nginx proxies /api → http://api:8080/api
  apiUrl: '/api',

  // ✅ WebSocket endpoint proxied by Nginx → http://api:8080/ws-chat
  wsUrl: '/ws-chat',

  // ✅ Google OAuth redirect from deployed frontend
  googleRedirectUri: 'http://localhost/chats/oauth2/callback',

  // ✅ Disable debug logs for production build
  enableDebugLogs: false
};
