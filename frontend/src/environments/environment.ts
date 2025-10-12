/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * 🌱 Environment Configuration — Development
 * -------------------------------------------
 * Used when running Angular locally (`ng serve`).
 * Points to a local backend on http://localhost:8080.
 */

export const environment = {
  production: false,

  /** 🌐 Local backend API (Spring Boot) */
  apiUrl: 'http://localhost:8080/api',

  /** 🔌 Local WebSocket endpoint */
  wsUrl: 'http://localhost:8080/ws-chat',

  /** 🔑 Google OAuth redirect for local testing */
  googleRedirectUri: 'http://localhost:4200/chats/oauth2/callback',

  /** 🐞 Enables console debug logs */
  enableDebugLogs: true,
};
