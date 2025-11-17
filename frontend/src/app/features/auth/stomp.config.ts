/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {RxStompConfig} from '@stomp/rx-stomp';
import {environment} from '../../../environments/environment.prod';

export const smartChatStompConfig: RxStompConfig = {
  // 🌐 WebSocket endpoint
  brokerURL: `${environment.apiUrl.replace('/api', '')}/ws-chat`,

  // 🧠 Connection headers
  connectHeaders: {},

  // ⚡ Heartbeat (keep-alive)
  heartbeatIncoming: 5000,
  heartbeatOutgoing: 5000,

  // ♻️ Auto-reconnect with backoff
  reconnectDelay: 3000,

  // 🪵 Debugging
  debug: (msg: string): void => {
    if (environment.production) return;
    console.log('[RxStomp]', msg);
  },
};
