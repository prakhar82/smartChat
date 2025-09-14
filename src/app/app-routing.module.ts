import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RegisterComponent } from './auth/register/register.component';
import { LoginComponent } from './auth/login/login.component';
import { SyncContactsComponent  } from './contacts/sync/sync.component';
import { ChatListComponent } from './chats/list/chat-list.component';
import { ChatWindowComponent } from './chats/window/chat-window.component';
import { AuthGuard } from './auth/auth.guard';

const routes: Routes = [
  { path: 'register', component: RegisterComponent },
  { path: 'login', component: LoginComponent },
  { path: 'sync', component: SyncContactsComponent, canActivate: [AuthGuard] },
  {
    path: 'chats',
    canActivate: [AuthGuard],
    children: [
      { path: '', component: ChatListComponent },
      { path: ':id', component: ChatWindowComponent },
    ],
  },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { useHash: true })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
