/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * SmartChat Application Bootstrap
 * --------------------------------
 * - Initializes Angular root application.
 * - Loads environment configuration dynamically.
 * - Adds startup logs for debugging & environment verification.
 */

import {bootstrapApplication} from '@angular/platform-browser';
import {AppComponent} from './app/app.component';
import {appConfig} from './app/app.config';
import {environment} from './environments/environment';

/**
 * Log environment and build details
 * (helps confirm correct environment file is loaded)
 */
console.log('%c[SmartChat] 🚀 Bootstrapping Application', 'color:#4CAF50;font-weight:bold;');
console.log('[SmartChat] 🌐 Environment:', environment.production ? 'PRODUCTION' : 'DEVELOPMENT');
console.log('[SmartChat] 🔗 API URL:', environment.apiUrl);
console.log('[SmartChat] 🔌 WebSocket URL:', environment.wsUrl);
if (environment.enableDebugLogs) {
  console.log('[SmartChat] 🐞 Debug logs enabled');
}

/**
 * Bootstrap root Angular application
 */
bootstrapApplication(AppComponent, appConfig)
  .then(() => {
    console.log('%c[SmartChat] ✅ Application bootstrapped successfully!', 'color:#00C853;font-weight:bold;');
  })
  .catch(err => {
    console.error('[SmartChat] ❌ Bootstrap failed:', err);
  });
