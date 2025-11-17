/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthService} from './auth.service';

/**
 * ==========================================================
 * 🧠 AuthGuard
 * ----------------------------------------------------------
 * Prevents access to protected routes if:
 * - The user is not logged in, or
 * - The token is expired.
 * Redirects unauthorized users to `/auth/login`.
 * ==========================================================
 */
export const AuthGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn() && !auth.isTokenExpired()) {
    return true;
  }

  console.warn('[AuthGuard] ⚠️ Missing or expired token → redirecting to /auth/login');
  auth.logout();
  router.navigate(['/auth/login']);
  return false;
};
