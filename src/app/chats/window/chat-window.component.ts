import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common'; // For *ngFor and *ngIf
import { FormsModule } from '@angular/forms';   // For ngModel
import { ChatService } from '../chat.service';

@Component({
  selector: 'app-chat-window',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-window.component.html'
})
export class ChatWindowComponent implements OnInit {
  contactId!: number;
  messages: any[] = [];
  newMessage = '';
  userId = Number(localStorage.getItem('userId')) || null; // Replace with actual logged-in user id

  constructor(private route: ActivatedRoute, private chatService: ChatService) {}

  ngOnInit(): void {
    this.contactId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  load(): void {
    this.chatService.getChatHistory(this.contactId, this.userId).subscribe({
      next: data => this.messages = data,
      error: err => console.error('Failed to fetch messages', err)
    });
  }

  send(): void {
    if (!this.newMessage.trim()) return;
    this.chatService.sendMessage(this.userId, this.contactId, this.newMessage).subscribe({
      next: msg => {
        // Prepend latest
        this.messages.unshift(msg);
        this.newMessage = '';
      },
      error: err => console.error('Failed to send message', err)
    });
  }
}
