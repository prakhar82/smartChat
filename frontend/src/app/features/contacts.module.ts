/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * ==========================================================
 * 📇 ContactsFeatureModule
 * ----------------------------------------------------------
 * Handles routes under /contacts:
 * - /contacts/sync
 * ==========================================================
 */

import {NgModule} from '@angular/core';
import {RouterModule} from '@angular/router';
import {AuthGuard} from './auth/auth.guard';

@NgModule({
  imports: [
    RouterModule.forChild([
      {
        path: 'sync',
        canActivate: [AuthGuard],
        loadComponent: () =>
          import('./contacts/sync/sync-contacts.component').then(
            (m) => m.SyncContactsComponent
          ),
      },
      {path: '', redirectTo: 'sync', pathMatch: 'full'},
    ]),
  ],
})
export class ContactsFeatureModule {
}
