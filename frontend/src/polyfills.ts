/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/***************************************************************************************************
 * BROWSER POLYFILLS
 */

// ✅ Fix "global is not defined" for stomp/sockjs
// Fix for Node globals in browser build
(window as any).global = window;
(window as any).process = { env: {} };
(window as any).Buffer = [];
