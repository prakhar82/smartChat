/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

export const environment = {
  production: true,
  apiUrl: '/api', // nginx proxy handles backend
  googleRedirectUri: 'http://localhost/chats/oauth2/callback'
  // 👆 Must exactly match what you added in Google Cloud Console
};
