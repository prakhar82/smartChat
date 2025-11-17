/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthService} from '../auth.service';

/**
 * ==========================================================
 * 🚪 LoginGuard
 * ----------------------------------------------------------
 * Prevents logged-in users from accessing /auth routes.
 * Redirects them to `/chats` if they’re already authenticated.
 * ==========================================================
 */
export const LoginGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) {
    console.info('[LoginGuard] User already logged in → redirecting to /chats');
    router.navigate(['/chats']);
    return false;
  }

  return true; // Allow access to /auth/login or /auth/register
};
