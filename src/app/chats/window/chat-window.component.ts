import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService, ChatMessage } from '../chat.service';

@Component({
  selector: 'app-chat-window',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-window.component.html',
  styleUrls: ['./chat-window.component.css']
})
export class ChatWindowComponent implements OnInit {
  contactId!: number;
  messages: ChatMessage[] = [];
  newMessage = '';
  userId = Number(localStorage.getItem('userId')) || null;

  constructor(
    private route: ActivatedRoute,
    private chatService: ChatService
  ) {}

  ngOnInit(): void {
    this.contactId = Number(this.route.snapshot.paramMap.get('id'));

    if (this.userId) {
      this.chatService.connectWebSocket(this.userId);

      this.chatService.messages$.subscribe((msg: ChatMessage | null) => {
        if (!msg) return; // ✅ safe guard

        if (
          (msg.senderId === this.contactId && msg.receiverId === this.userId) ||
          (msg.senderId === this.userId && msg.receiverId === this.contactId)
        ) {
          this.messages.unshift(msg);
          // ✅ if window is active and user is receiver → mark as READ
          if (msg.receiverId === this.userId && document.hasFocus()) {
            this.chatService.updateStatus(msg.id!, 'READ').subscribe();
          }
        }
      });
    }

    this.loadHistory();
  }

  ngAfterViewInit(): void {
    window.addEventListener('focus', () => {
      this.messages
        .filter(m => m.receiverId === this.userId && m.status !== 'READ')
        .forEach(m => this.chatService.updateStatus(m.id!, 'READ').subscribe());
    });
  }


  loadHistory(): void {
    if (!this.userId) return;
    this.chatService.getChatHistory(this.contactId, this.userId).subscribe({
      next: (data: ChatMessage[]) => (this.messages = data),
      error: (err: any) => console.error('Failed to fetch messages', err),
    });
  }

  send(): void {
    if (!this.newMessage.trim() || !this.userId) return;

    this.chatService.sendMessage(this.userId, this.contactId, this.newMessage);

    // Optimistic UI update
    this.messages.unshift({
      id: Date.now(),
      senderId: this.userId,
      receiverId: this.contactId,
      message: this.newMessage,
      emoji: undefined,
      timestamp: new Date().toISOString(),
    });

    this.newMessage = '';
  }
}
