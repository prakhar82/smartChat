/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {RegisterComponent} from './auth/register/register.component';
import {LoginComponent} from './auth/login/login.component';
import {SyncContactsComponent} from './contacts/sync/sync.component';
import {ContactListComponent} from './chats/list/contact-list.component';
import {ChatWindowComponent} from './chats/window/chat-window.component';

import {AuthGuard} from './auth/auth.guard';
import {LoginGuard} from './auth/login/login.guard';
import {ChatsPageComponent} from './chats/page/chats-page.component';

const routes: Routes = [
  {path: 'register', component: RegisterComponent, canActivate: [LoginGuard]},
  {path: 'login', component: LoginComponent, canActivate: [LoginGuard]},
  {path: 'sync', component: SyncContactsComponent, canActivate: [AuthGuard]},
  {
    path: 'chats',
    component: ChatsPageComponent,
    canActivate: [AuthGuard],
    children: [
      {
        path: '',
        component: ContactListComponent, // loads into primary (left <router-outlet>)
      },
      {
        path: ':id',
        component: ChatWindowComponent,
        outlet: 'chat', // ✅ goes into right <router-outlet name="chat">
      },
    ],
  },
  {path: '', redirectTo: '/login', pathMatch: 'full'},
  // optional catch-all route for invalid URLs
  {path: '**', redirectTo: '/login'}
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {useHash: true})],
  exports: [RouterModule],
})
export class AppRoutingModule {
}
