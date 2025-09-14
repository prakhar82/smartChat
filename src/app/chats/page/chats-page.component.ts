import {Component, Input, OnInit} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterOutlet } from '@angular/router';
import { ChatListComponent } from './../list/chat-list.component';


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
  @Input() userId!: string; // expecting string
  constructor(private router: Router) {}

  ngOnInit(): void {
    const storedUserId = localStorage.getItem('userId');

    if (!storedUserId) {
      this.router.navigate(['/login']);
      return;
    }

    this.userId = storedUserId; // ✅ Directly assign as string
  }
}
