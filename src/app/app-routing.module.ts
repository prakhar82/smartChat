import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { ContactSyncComponent } from './contacts/sync/sync.component';
import { ChatListComponent } from './chats/list/chat-list.component';
import { ChatWindowComponent } from './chats/window/chat-window.component';
import { AuthGuard } from './auth/auth.guard';

const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'contacts-sync', component: ContactSyncComponent, canActivate: [AuthGuard] },
  { path: 'chat-list', component: ChatListComponent, canActivate: [AuthGuard] },
  { path: 'chat-window/:id', component: ChatWindowComponent, canActivate: [AuthGuard] },
  { path: '', redirectTo: 'login', pathMatch: 'full' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}