/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import { Component, OnInit } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ChatListComponent } from './../list/chat-list.component';
import { ChatService } from './../chat.service';

@Component({
  selector: 'app-chats-page',
  standalone: true,
  imports: [CommonModule, RouterOutlet, ChatListComponent],
  template: `
    <div class="chats-container">
      <div class="chat-list">
        <app-chat-list [userId]="userId"></app-chat-list>
      </div>
      <div class="chat-window">
        <router-outlet></router-outlet>
      </div>
    </div>
  `,
  styleUrls: ['./chats-page.component.css']
})
export class ChatsPageComponent implements OnInit {
  userId = Number(localStorage.getItem('userId')) || null;

  constructor(private chatService: ChatService, private router: Router) {}

  ngOnInit() {
    if (this.userId != null) {
      this.chatService.getMatchedContacts(this.userId).subscribe({
        next: (contacts) => {
          if (contacts && contacts.length > 0) {
            // 🚀 Redirect to first contact if no child route is active
            const currentUrl = this.router.url;
            if (currentUrl === '/chats') {
              this.router.navigate(['/chats', contacts[0].id]);
            }
          }
        },
        error: (err) => console.error('Failed to fetch contacts for auto-redirect', err)
      });
    }
  }
}
