/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

export const environment = {
  production: true,

  // ✅ Local dev via gateway: change to 'https://localhost:8080/api' if not using nginx
  apiUrl: '/api',

  // ✅ WebSocket relay
  wsUrl: '/ws-chat',

  stompConfig: {
    user: 'smartchat_user',
    pass: 'smartchat123',
    vhost: 'smartchat',
  },

  googleRedirectUri: 'https://app.smartchat.ai/api/contact/google/callback',
  enableDebugLogs: false,
};
