/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * SmartChat © 2025
 * Centralized Route Configuration (Standalone + Lazy-loaded)
 */

import {Routes} from '@angular/router';
import {HomeComponent} from './features/home/home.component';

export const routes: Routes = [
  {path: '', redirectTo: 'home', pathMatch: 'full'},
  {path: 'home', component: HomeComponent},

  {
    path: 'auth',
    loadChildren: () =>
      import('./features/auth.module').then((m) => m.AuthFeatureModule),
  },
  {
    path: 'chats',
    loadChildren: () =>
      import('./features/chats.module').then((m) => m.ChatsFeatureModule),
  },
  {
    path: 'contacts',
    loadChildren: () =>
      import('./features/contacts.module').then((m) => m.ContactsFeatureModule),
  },

  {path: '**', redirectTo: 'home'},
];
