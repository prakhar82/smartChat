/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * ==========================================================
 * 🧩 AuthFeatureModule
 * ----------------------------------------------------------
 * Handles routes under /auth:
 * - /auth/login
 * - /auth/register
 * ==========================================================
 */

import {NgModule} from '@angular/core';
import {RouterModule} from '@angular/router';

@NgModule({
  imports: [
    RouterModule.forChild([
      {
        path: 'login',
        loadComponent: () =>
          import('./auth/login/login.component').then((m) => m.LoginComponent),
      },
      {
        path: 'register',
        loadComponent: () =>
          import('./auth/register/register.component').then(
            (m) => m.RegisterComponent
          ),
      },
      {path: '', redirectTo: 'login', pathMatch: 'full'},
    ]),
  ],
})
export class AuthFeatureModule {
}
