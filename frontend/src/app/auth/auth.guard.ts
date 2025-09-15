/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * AuthGuard: prevents routing to protected pages if not authenticated
 */

import {Injectable} from '@angular/core';
import {CanActivate, Router, UrlTree,} from '@angular/router';
import {AuthService} from './auth.service';

@Injectable({providedIn: 'root'})
export class AuthGuard implements CanActivate {
  constructor(private authService: AuthService, private router: Router) {
  }

  canActivate(): boolean | UrlTree {
    if (this.authService.isLoggedIn()) {
      // ✅ Redirect logged-in users to contacts sync page
      return true;
    }
    return this.router.parseUrl('/login');
  }
}

