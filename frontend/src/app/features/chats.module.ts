/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * ==========================================================
 * 💬 ChatsFeatureModule
 * ----------------------------------------------------------
 * Handles routes under /chats:
 * - /chats
 * - /chats/:id
 * ==========================================================
 */

import {NgModule} from '@angular/core';
import {RouterModule} from '@angular/router';
import {AuthGuard} from './auth/auth.guard';

@NgModule({
  imports: [
    RouterModule.forChild([
      {
        path: '',
        canActivate: [AuthGuard],
        loadComponent: () =>
          import('./chats/page/chats-page.component').then(
            (m) => m.ChatsPageComponent
          ),
      },
      {
        path: ':id',
        canActivate: [AuthGuard],
        loadComponent: () =>
          import('./chats/page/chats-page.component').then(
            (m) => m.ChatsPageComponent
          ),
      },
    ]),
  ],
})
export class ChatsFeatureModule {
}
