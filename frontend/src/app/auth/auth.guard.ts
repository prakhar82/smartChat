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

export const AuthGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) {
    return true; // ✅ Allow access
  } else {
    console.warn('🔒 AuthGuard blocked access. Redirecting to /login');
    router.navigate(['/login']);
    return false;
  }
};
