/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.example.smartchat',
  appName: 'smartChat',
  webDir: '../frontend/dist',
  server: {
    url: 'http://10.0.2.2', // Android emulator localhost mapping
    cleartext: true
  }
};

export default config;
