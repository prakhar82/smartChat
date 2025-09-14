/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any me dium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { ChatsPageComponent } from './chats/page/chats-page.component';
import { ChatWindowComponent } from './chats/window/chat-window.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },

  {
    path: 'chats',
    component: ChatsPageComponent,
    children: [
      { path: ':id', component: ChatWindowComponent } // nested outlet
    ]
  },

  { path: '**', redirectTo: '/login' },
];
