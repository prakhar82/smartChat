/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * SmartChat — Final WebSocket URL Resolver (No Query Token)
 * ---------------------------------------------------------------
 * - Prevents double SockJS connections
 * - Avoids token in /info probe
 * - Safe for Angular DI (fixes TS2729)
 * - Use only for building the base SockJS endpoint
 */

import {environment} from '../../environments/environment';

/**
 * Resolve final SockJS base URL.
 *
 * ❗ IMPORTANT:
 * Do NOT append token here.
 * SockJS calls `/ws-chat/info` FIRST → must NOT include JWT
 * or SockJS retries twice → double CONNECT → anonymous principal.
 */
export function resolveWebSocketUrl(): string {
  try {
    const loc = window.location;

    // SockJS MUST use HTTP(S), not WS(S)
    const protocol = loc.protocol === 'https:' ? 'https:' : 'http:';
    const host = loc.host;

    // Normalize /ws-chat path
    const rawPath = environment.wsUrl.startsWith('/')
      ? environment.wsUrl
      : '/' + environment.wsUrl;

    // Remove any accidental ws:// or wss:// prefixes
    const safePath = rawPath.replace(/^wss?:\/\//, '');

    const url = `${protocol}//${host}${safePath}`;

    console.log('[resolveWebSocketUrl] Final SockJS URL →', url);
    return url;

  } catch (err) {
    console.error('[resolveWebSocketUrl] Fallback due to error:', err);
    return '/ws-chat';
  }
}
