/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router, RouterOutlet} from '@angular/router';
import {ChatListComponent} from './../list/chat-list.component';
import {AuthService} from '../../auth/auth.service';

@Component({
  selector: 'app-chats-page',
  standalone: true,
  imports: [CommonModule, RouterOutlet, ChatListComponent],
  template: `
    <div class="chats-container">
      <div class="chat-list-panel">
        <app-chat-list [userId]="userId"></app-chat-list>
      </div>
      <div class="chat-window-panel">
        <router-outlet></router-outlet>
      </div>
    </div>
  `,
  styleUrls: ['./chats-page.component.css']
})
export class ChatsPageComponent implements OnInit {
  userId!: number;

  constructor(private auth: AuthService, private router: Router) {
  }

  ngOnInit(): void {
    const storedUserId = this.auth.getUserId(); // typically string from localStorage

    if (!storedUserId) {
      this.router.navigate(['/login']);
      return;
    }

    this.userId = Number(storedUserId); // ✅ always convert to number
  }
}
