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

export const routes: Routes = [
  {path: '', loadComponent: () => import('./home/home.component').then(m => m.HomeComponent)}, // 👈 show HomeComponent
  {path: 'register', loadComponent: () => import('./auth/register/register.component').then(m => m.RegisterComponent)},
  {path: 'login', loadComponent: () => import('./auth/login/login.component').then(m => m.LoginComponent)},
  {
    path: 'chats',
    loadComponent: () => import('./chats/page/chats-page.component').then(m => m.ChatsPageComponent),
    children: [
      {path: '', loadComponent: () => import('./chats/list/contact-list.component').then(m => m.ContactListComponent)},
      {
        path: ':id',
        loadComponent: () => import('./chats/window/chat-window.component').then(m => m.ChatWindowComponent),
        outlet: 'chat'
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {useHash: false})],
  exports: [RouterModule]
})
export class AppRoutingModule {
}
