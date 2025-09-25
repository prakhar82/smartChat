/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

// src/app/app-routing.module.ts
import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {RegisterComponent} from './auth/register/register.component';
import {LoginComponent} from './auth/login/login.component';
import {SyncContactsComponent} from './contacts/sync/sync.component';
import {ContactListComponent} from './chats/list/contact-list.component';
import {ChatWindowComponent} from './chats/window/chat-window.component';

import {AuthGuard} from './auth/auth.guard'; // ✅ class guard
import {LoginGuard} from './auth/login/login.guard'; // ✅ function guard

const routes: Routes = [
  {path: 'register', component: RegisterComponent, canActivate: [LoginGuard]},
  {path: 'login', component: LoginComponent, canActivate: [LoginGuard]},
  {path: 'sync', component: SyncContactsComponent, canActivate: [AuthGuard]},
  {
    path: 'chats',
    canActivate: [AuthGuard],
    children: [
      {path: '', component: ContactListComponent},
      {path: ':id', component: ChatWindowComponent},
    ],
  },
  {path: '', redirectTo: '/login', pathMatch: 'full'},
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {useHash: true})],
  exports: [RouterModule],
})
export class AppRoutingModule {
}
