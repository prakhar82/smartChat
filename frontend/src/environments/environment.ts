/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

export const environment = {
  production: false,

  /** 🌐 Local backend API (Spring Boot) */
  apiUrl: 'http://localhost:8080/api',

  /** 🔌 Local WebSocket endpoint (RabbitMQ Web STOMP) */
  wsUrl: 'ws://localhost:15674/ws',

  /** 🧠 RabbitMQ STOMP credentials (for WebSocket client) */
  stompConfig: {
    user: 'smartchat_user',
    pass: 'smartchat123',
    vhost: 'smartchat',
  },

  /** 🔑 Google OAuth redirect for local testing */
  googleRedirectUri: 'http://localhost:4200/chats/oauth2/callback',

  /** 🐞 Enables console debug logs */
  enableDebugLogs: true,
};
