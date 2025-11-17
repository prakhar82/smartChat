/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * Waits until the backend STOMP relay is ready.
 * Polls /api/internal/stomp-ready up to 10 times (every 1s).
 * Ensures Spring STOMP broker relay is fully initialized.
 */
export async function waitForStompReady(): Promise<boolean> {
  const maxAttempts = 10;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const resp = await fetch('/api/internal/stomp-ready', {cache: 'no-store'});
      if (resp.ok) {
        const data = await resp.json();
        if (data.ready) {
          console.log(`[waitForStompReady] 🟢 STOMP relay ready (attempt ${attempt})`);
          return true;
        }
      }
    } catch (err) {
      console.warn(`[waitForStompReady] ⚠️ STOMP readiness check failed (attempt ${attempt})`, err);
    }

    console.log(`[waitForStompReady] ⏳ Waiting for STOMP relay... (${attempt}/10)`);
    await new Promise((r) => setTimeout(r, 1000));
  }

  console.error('[waitForStompReady] ❌ STOMP relay not ready after 10s — giving up');
  return false;
}
