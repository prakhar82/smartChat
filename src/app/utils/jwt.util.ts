/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

export function getJwtExpiry(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    if (payload?.exp) {
      return payload.exp * 1000; // convert to ms
    }
  } catch (e) {
    console.error('Invalid JWT token', e);
  }
  return null;
}
