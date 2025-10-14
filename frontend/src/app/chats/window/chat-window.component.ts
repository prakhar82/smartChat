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
 * - Supports desktop and mobile
 * - Subscribes to live message + typing events
 * - Supports emoji reactions ❤️ 😂 👍 😢 😮
 * ---------------------------------------------------------
 */

import {Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subscription} from 'rxjs';
import {ChatMessage, ChatService} from '../chat.service';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {AuthService} from '../../auth/auth.service';
import {PresenceService} from '../../shared/presence/presence.service';
import {LastSeenPipe} from '../../shared/presence/last-seen.pipe';


@Component({
  selector: 'app-chat-window',
  standalone: true,
  imports: [CommonModule, FormsModule, LastSeenPipe],
  templateUrl: './chat-window.component.html',
  styleUrls: ['./chat-window.component.scss'],
})
export class ChatWindowComponent implements OnInit, OnDestroy {
  /* =========================================================
   * 📥 Inputs
   * ========================================================= */
  @Input() contactId!: number;
  @Input() contactInfo?: MatchedContact;
  @Input() isMobileView = false;

  /* =========================================================
   * 📤 Outputs
   * ========================================================= */
  @Output() back = new EventEmitter<void>();
  @Output() closeChat = new EventEmitter<void>();

  /* =========================================================
   * 🔧 View & State
   * ========================================================= */
  @ViewChild('messageContainer') messageContainer!: ElementRef<HTMLDivElement>;

  messages: ChatMessage[] = [];
  newMessage = '';
  isOtherTyping = false;
  isConnected = true;
  currentUserId!: number;
  private subs = new Subscription();

  /** 💞 Emoji Reaction State */
  hoveredMessage: string | null = null;
  reactionEmojis: string[] = ['❤️', '😂', '👍', '😢', '😮'];

  /** 💞 Floating Reactions (animated hearts) */
  floatingReactions: { id: number; emoji: string; x: number; y: number }[] = [];
  private reactionCounter = 0;

  constructor(
    private readonly chatService: ChatService,
    private readonly contactService: ContactService,
    private readonly authService: AuthService,
    private readonly presenceService: PresenceService
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

    // ✅ Connection status
    this.subs.add(
      this.chatService.getConnectionStatus().subscribe((status) => {
        this.isConnected = status;
        if (!status) console.warn('[ChatWindow] ⚠️ Disconnected from chat server');
      })
    );

    // ✅ Incoming messages
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
        }
      })
    );

    // ✅ Typing event stream
    this.subs.add(
      this.chatService.getTypingStream().subscribe((event) => {
        if (
          event?.fromId === this.contactInfo?.matchedUserId &&
          event?.toId === this.currentUserId
        ) {
          this.isOtherTyping = true;
          setTimeout(() => (this.isOtherTyping = false), 2000);
        }
      })
    );

    // ✅ Online/offline status
    this.subs.add(
      this.chatService.getOnlineStatus().subscribe((status) => {
        if (!status) return;

        const contactId = Number(
          this.contactInfo?.matchedUserId ||
          this.contactInfo?.contactId ||
          this.contactId
        );

        if (status.userId === contactId) {
          this.contactInfo = {
            ...this.contactInfo!,
            online: status.online,
          };
          console.log('[ChatWindow] 🟢 Contact online:', status.online);
        }
      })
    );

    // ✅ Live online/offline updates from PresenceService
    this.subs.add(
      this.presenceService.getPresenceStream().subscribe((presenceMap) => {
        const targetId = this.contactInfo?.matchedUserId ?? this.contactId;
        if (!targetId) return;

        const presence = presenceMap.get(String(targetId));
        if (!presence) return;

        const {online, lastSeen} = presence;

        this.contactInfo = {
          ...this.contactInfo!,
          online,
          lastSeen,
        };

        console.log(
          `[ChatWindow] 🟢 Presence update → ${online ? 'Online' : `Last seen ${lastSeen}`}`
        );
      })
    );
  }

  ngOnDestroy(): void {
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
      console.error('[ChatWindow] ❌ Invalid receiver ID');
      return;
    }

    const outgoing: ChatMessage = {
      senderId: this.currentUserId,
      receiverId,
      message: text,
      timestamp: new Date().toISOString(),
      status: 'SENT',
    };

    // ✅ Use unified ChatService method
    this.chatService.sendMessage(this.currentUserId, receiverId, text);

    this.messages.push(outgoing);
    this.newMessage = '';
    this.scrollToBottom();
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

    this.chatService.sendTyping(this.currentUserId, receiverId);
  }

  /* =========================================================
   * 💞 Reactions
   * ========================================================= */
  addReaction(msg: ChatMessage, emoji: string): void {
    if (!msg.reactions) msg.reactions = [];
    if (!msg.reactions.includes(emoji)) msg.reactions.push(emoji);
    this.hoveredMessage = msg.id ?? null;
    this.triggerFloatingReaction(emoji);
    console.log('[ChatWindow] 💞 Reaction added:', emoji, '→', msg.id);
  }

  triggerFloatingReaction(emoji: string): void {
    const container = this.messageContainer?.nativeElement;
    if (!container) return;

    const rect = container.getBoundingClientRect();
    const x = rect.width / 2 + (Math.random() * 60 - 30);
    const y = rect.height - 40;

    const id = ++this.reactionCounter;
    this.floatingReactions.push({id, emoji, x, y});

    setTimeout(() => {
      this.floatingReactions = this.floatingReactions.filter((r) => r.id !== id);
    }, 2000);
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
    this.back.emit();
  }

  onCloseChat(): void {
    this.closeChat.emit();
  }
}
