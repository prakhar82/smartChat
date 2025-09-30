/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, ViewChild,} from '@angular/core';
import {ActivatedRoute, ParamMap, Router} from '@angular/router';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subscription} from 'rxjs';
import {ChatMessage, ChatService} from '../chat.service';
import {AuthService} from '../../auth/auth.service';

@Component({
  selector: 'app-chat-window',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-window.component.html',
  styleUrls: ['./chat-window.component.css'],
})
export class ChatWindowComponent
  implements OnInit, AfterViewChecked, OnDestroy {
  myUserId!: number;
  otherUserId!: number;

  messages: ChatMessage[] = [];
  newMessage = '';
  typingTimeout: any;
  showTypingTooltip = false;
  truncatedMessage = '';

  isMobile = window.innerWidth < 768; // ✅ add this

  private routeSub!: Subscription;
  private wsSub!: Subscription;

  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  constructor(
    private route: ActivatedRoute,
    private router: Router,             // ✅ add Router for goBack()
    private chatService: ChatService,
    private authService: AuthService
  ) {
  }

  ngOnInit(): void {
    const uid = this.authService.getUserId();
    if (!uid) {
      console.error('[ChatWindow] ⚠️ User not logged in');
      return;
    }
    this.myUserId = Number(uid);

    this.chatService.connectWebSocket(this.myUserId);

    this.wsSub = this.chatService.messages$.subscribe((msg) => {
      if (!msg) return;

      if (msg.type === 'DELETE' && msg.messageId) {
        const target = this.messages.find((m) => m.id === msg.messageId);
        if (target) {
          target.message = 'This message was deleted';
          target.fileUrl = null;
          target.fileName = null;
          target.emoji = null;
        }
        return;
      }

      if (msg.senderId === this.otherUserId) {
        this.messages.push(msg);
        this.scrollToBottom();
      }
    });

    this.routeSub = this.route.paramMap.subscribe((pm: ParamMap) => {
      const idStr = pm.get('id');
      this.otherUserId = idStr ? Number(idStr) : NaN;
      this.loadConversation();
    });
  }

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  ngOnDestroy(): void {
    clearTimeout(this.typingTimeout);
    this.routeSub?.unsubscribe();
    this.wsSub?.unsubscribe();
  }

  private loadConversation() {
    if (!this.otherUserId || !this.myUserId) {
      this.messages = [];
      return;
    }

    this.chatService.getChatHistory(this.otherUserId).subscribe({
      next: (msgs) => {
        this.messages = msgs || [];
        this.scrollToBottom();
      },
      error: (err) => {
        console.error('[ChatWindow] ❌ Failed to load messages', err);
        this.messages = [];
      },
    });
  }

  private scrollToBottom() {
    try {
      const el = this.messagesContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    } catch {
    }
  }

  sendMessage() {
    const text = this.newMessage.trim();
    if (!text || !this.otherUserId) return;

    const localMsg: ChatMessage = {
      senderId: this.myUserId,
      receiverId: this.otherUserId,
      message: text,
      timestamp: new Date().toISOString(),
      status: 'SENT',
    };
    this.messages.push(localMsg);
    this.scrollToBottom();

    this.chatService.sendMessage(this.myUserId, this.otherUserId, text);

    this.newMessage = '';
    this.showTypingTooltip = false;
  }

  deleteMessage(messageId: number) {
    this.chatService.deleteMessage(messageId).subscribe({
      next: (updated) => {
        const target = this.messages.find((m) => m.id === updated.id);
        if (target) {
          target.message = updated.message;
          target.fileUrl = null;
          target.fileName = null;
          target.emoji = null;
        }
      },
      error: (err) =>
        console.error('[ChatWindow] ❌ Failed to delete message', err),
    });
  }

  onInput(event: any) {
    const textarea = event.target as HTMLTextAreaElement;
    textarea.style.height = 'auto';
    textarea.style.height = textarea.scrollHeight + 'px';

    if (!this.newMessage.trim()) {
      this.showTypingTooltip = false;
      clearTimeout(this.typingTimeout);
      return;
    }

    if (textarea.scrollHeight > 60) {
      this.showTypingTooltip = true;
      this.truncatedMessage =
        this.newMessage.length > 50
          ? this.newMessage.substring(0, 50) + '...'
          : this.newMessage;

      clearTimeout(this.typingTimeout);
      this.typingTimeout = setTimeout(
        () => (this.showTypingTooltip = false),
        3000
      );
    } else {
      this.showTypingTooltip = false;
      clearTimeout(this.typingTimeout);
    }
  }

  trackByIdx(index: number): number {
    return index;
  }

  // ✅ fix unresolved goBack()
  goBack(): void {
    this.router.navigate([{outlets: {primary: ['chats'], chat: null}}]);
  }
}
