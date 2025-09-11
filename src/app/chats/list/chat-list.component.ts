import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';   // ✅ for *ngFor
import { ChatService } from '../chat.service';

@Component({
  selector: 'app-chat-list',
  standalone: true,
  imports: [CommonModule],   // ✅ add here
  templateUrl: './chat-list.component.html'
})
export class ChatListComponent implements OnInit {
  @Input() userId: number | null = null;
  contacts: any[] = [];

  constructor(private chatService: ChatService) {}

  ngOnInit() {
    if (this.userId != null) {
      this.chatService.getMatchedContacts(this.userId).subscribe({
        next: (res) => this.contacts = res
      });
    }
  }

  openChat(c: any) {
    console.log('Opening chat with', c);
  }
}
