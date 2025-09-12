import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ChatService } from '../chat.service';

@Component({
  selector: 'app-chat-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './chat-list.component.html',
  styleUrls: ['./chat-list.component.css']
})
export class ChatListComponent implements OnInit {
  @Input() userId: number | null = null;
  contacts: any[] = [];
  loading = true;
  error = '';

  constructor(private chatService: ChatService, private router: Router) {}

  ngOnInit() {
    if (!this.userId) {
      this.error = 'No user logged in';
      this.loading = false;
      return;
    }

    this.chatService.connectWebSocket(this.userId); // 🔗 connect once

    this.chatService.getMatchedContacts(this.userId).subscribe({
      next: (res) => {
        this.contacts = res;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load contacts', err);
        this.error = 'Could not load contacts';
        this.loading = false;
      }
    });
  }

  openChat(contact: any) {
    if (!this.userId) return;
    this.router.navigate(['/chats', contact.id]);
  }
}
