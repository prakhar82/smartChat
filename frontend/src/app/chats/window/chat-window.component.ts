/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * 💬 ChatWindowComponent
 * ---------------------------------------------------------
 * Renders the message thread and input box for a given contact.
 * - Supports both desktop and mobile modes.
 * - Subscribes to live message and typing events.
 * - Emits close/back events for parent layout.
 * ---------------------------------------------------------
 */

import {Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subscription} from 'rxjs';
import {ChatMessage, ChatService} from '../chat.service';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {AuthService} from '../../auth/auth.service';

@Component({
  selector: 'app-chat-window',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-window.component.html',
  styleUrls: ['./chat-window.component.scss'],
})
export class ChatWindowComponent implements OnInit, OnDestroy {
  /* =========================================================
   * 📥 Inputs
   * ========================================================= */

  /** 🔹 ID of the contact currently being chatted with */
  @Input() contactId!: number;

  /** 👤 Full contact information (preferred for UI display) */
  @Input() contactInfo?: MatchedContact;

  /** 📱 Indicates mobile mode for showing back button */
  @Input() isMobileView = false;

  /* =========================================================
   * 📤 Outputs
   * ========================================================= */

  /** ⬅️ Event fired when user navigates back (mobile only) */
  @Output() back = new EventEmitter<void>();

  /** ❌ Event fired when parent wants to close the chat window */
  @Output() closeChat = new EventEmitter<void>();

  /* =========================================================
   * 🔧 View & State
   * ========================================================= */

  /** 💬 Container reference for scroll control */
  @ViewChild('messageContainer') messageContainer!: ElementRef<HTMLDivElement>;

  /** 🗨️ Chat messages list */
  messages: ChatMessage[] = [];

  /** 📝 Input model for new messages */
  newMessage = '';

  /** 🧠 Flag if other user is typing */
  isOtherTyping = false;

  /** 👤 Logged-in user ID */
  currentUserId!: number;

  /** 🧹 Subscription manager */
  private subs = new Subscription();

  constructor(
    private readonly chatService: ChatService,
    private readonly contactService: ContactService,
    private readonly authService: AuthService
  ) {
  }

  /* =========================================================
   * 🚀 Lifecycle
   * ========================================================= */
  ngOnInit(): void {
    console.log('[ChatWindow] 🚀 Init with contactId =', this.contactId);

    this.currentUserId = Number(this.authService.getUserId());

    if (!this.contactInfo && !this.contactId) {
      console.warn('[ChatWindow] ⚠️ No valid contact info or ID provided');
      return;
    }

    // ✅ Subscribe to incoming messages
    this.subs.add(
      this.chatService.getMessages().subscribe((msg: ChatMessage) => {
        const receiverId = Number(
          this.contactInfo?.matchedUserId ||
          this.contactInfo?.contactId ||
          this.contactId
        );

        const isMine =
          msg.senderId === this.currentUserId && msg.receiverId === receiverId;
        const isFromContact =
          msg.receiverId === this.currentUserId && msg.senderId === receiverId;

        if (isMine || isFromContact) {
          this.messages.push(msg);
          this.scrollToBottom();
          console.log('[ChatWindow] 💬 New message added:', msg);
        }
      })
    );

    // ✅ Optional typing event stream
    if ((this.chatService as any).typing$) {
      this.subs.add(
        (this.chatService as any).typing$.subscribe((event: any) => {
          if (
            event?.fromId === this.contactInfo?.matchedUserId &&
            event?.toId === this.currentUserId
          ) {
            this.isOtherTyping = true;
            console.log('[ChatWindow] ✏️ Typing detected from contact');
            setTimeout(() => (this.isOtherTyping = false), 2000);
          }
        })
      );
    }
  }

  ngOnDestroy(): void {
    console.log('[ChatWindow] 🧹 Destroy and unsubscribe');
    this.subs.unsubscribe();
  }

  /* =========================================================
   * ✉️ Sending Messages
   * ========================================================= */
  sendMessage(): void {
    const text = this.newMessage.trim();
    if (!text) return;

    const receiverId = Number(
      this.contactInfo?.matchedUserId ||
      this.contactInfo?.contactId ||
      this.contactId
    );

    if (!receiverId) {
      console.error('[ChatWindow] ❌ Cannot send: invalid receiver ID');
      return;
    }

    const outgoing: ChatMessage = {
      senderId: this.currentUserId,
      receiverId,
      message: text,
      timestamp: new Date().toISOString(),
      status: 'SENT',
    };

    this.chatService.sendMessage(this.currentUserId, receiverId, text);
    this.messages.push(outgoing);
    this.newMessage = '';
    this.scrollToBottom();

    console.log('[ChatWindow] 🚀 Message sent →', outgoing);
  }

  /* =========================================================
   * 💭 Typing Notifications
   * ========================================================= */
  notifyTyping(): void {
    const receiverId = Number(
      this.contactInfo?.matchedUserId ||
      this.contactInfo?.contactId ||
      this.contactId
    );
    if (!receiverId) return;

    if (typeof (this.chatService as any).sendTyping === 'function') {
      (this.chatService as any).sendTyping(this.currentUserId, receiverId);
      console.log('[ChatWindow] ✏️ Typing event sent to', receiverId);
    }
  }

  /* =========================================================
   * 🔽 Auto Scroll
   * ========================================================= */
  private scrollToBottom(): void {
    setTimeout(() => {
      const container = this.messageContainer?.nativeElement;
      if (container) container.scrollTop = container.scrollHeight;
    }, 100);
  }

  /* =========================================================
   * 🧮 TrackBy Optimization
   * ========================================================= */
  trackByMsgId(index: number, msg: ChatMessage): string {
    return msg.id ?? index.toString();
  }

  /* =========================================================
   * 📱 Navigation / Close Events
   * ========================================================= */
  onBackClick(): void {
    console.log('[ChatWindow] ⬅️ Back button clicked');
    this.back.emit();
  }

  /** 🔙 Close chat window (emit event to parent) */
  onCloseChat(): void {
    console.log('[ChatWindow] 🔙 Close chat clicked');
    this.closeChat.emit();
  }
}
