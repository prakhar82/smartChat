/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, Observable, Subject, timer} from 'rxjs';
import {environment} from '../../environments/environment';
import {AuthService} from '../auth/auth.service';
import {Client, IMessage} from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export interface ChatMessage {
  id?: string;
  senderId: number;
  receiverId: number;
  message?: string;
  emoji?: string;
  fileUrl?: string;
  fileName?: string;
  status?: 'SENT' | 'DELIVERED' | 'READ';
  timestamp: string;
  reactions?: string[];
}

@Injectable({providedIn: 'root'})
export class ChatService implements OnDestroy {
  private readonly wsUrl = `${environment.apiUrl.replace('/api', '')}/ws-chat`;
  private stompClient: Client | null = null;

  private connectionState$ = new BehaviorSubject<boolean>(false);
  private messages$ = new Subject<ChatMessage>();
  private typing$ = new Subject<{ fromId: number; toId: number }>();
  private onlineStatus$ = new BehaviorSubject<{ userId: number; online: boolean } | null>(null);

  private pendingMessages: ChatMessage[] = [];
  private reconnecting = false;

  connection$: Observable<boolean> = this.connectionState$.asObservable();
  messagesStream$: Observable<ChatMessage> = this.messages$.asObservable();
  onlineStatusStream$: Observable<{ userId: number; online: boolean } | null> =
    this.onlineStatus$.asObservable();

  constructor(private authService: AuthService) {
  }

  /* =========================================================
   * 🌐 Connect STOMP
   * ========================================================= */
  connectStomp(userId: number | string): void {
    if (this.stompClient?.active) {
      console.log('[ChatService] ⚙️ Already connected');
      return;
    }

    const token = this.authService.getToken();
    if (!token) {
      console.warn('[ChatService] ⚠️ No auth token yet — retrying...');
      setTimeout(() => this.connectStomp(userId), 2000); // 🔧 Auto-retry until token exists
      return;
    }

    console.log('[ChatService] ⚙️ Initializing STOMP for user:', userId);
    const socket = new SockJS(this.wsUrl);

    this.stompClient = new Client({
      webSocketFactory: () => socket as any,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
        userId: String(userId),
      },
      debug: () => {
      },
      reconnectDelay: 2000, // 🔧 faster recovery
      heartbeatIncoming: 5000,
      heartbeatOutgoing: 5000,

      onConnect: () => {
        console.log('[ChatService] ✅ Connected to STOMP broker');
        this.connectionState$.next(true);
        this.reconnecting = false;
        this.subscribeToUserQueue(userId);
        this.onlineStatus$.next({userId: Number(userId), online: true});
        this.flushPendingMessages();
      },
      onDisconnect: () => {
        console.warn('[ChatService] ⚠️ STOMP disconnected');
        this.connectionState$.next(false);
        this.onlineStatus$.next({userId: Number(userId), online: false});
      },
      onStompError: (frame) => {
        console.error('[ChatService] ❌ Broker error:', frame.headers['message']);
        this.connectionState$.next(false);
        this.scheduleReconnect(userId);
      },
    });

    this.stompClient.activate();
  }

  /** ♻️ Schedule reconnection attempts */
  private scheduleReconnect(userId: number | string): void {
    if (this.reconnecting) return;
    this.reconnecting = true;
    console.warn('[ChatService] 🔁 Scheduling reconnect...');

    timer(3000).subscribe(() => {
      console.log('[ChatService] 🔌 Attempting STOMP reconnect...');
      this.connectStomp(userId);
    });
  }

  /* =========================================================
   * 💬 Subscriptions (messages + typing)
   * ========================================================= */
  private subscribeToUserQueue(userId: number | string): void {
    if (!this.stompClient) return;

    // 💬 Messages
    this.stompClient.subscribe('/user/queue/messages', (frame: IMessage) => {
      try {
        const msg: ChatMessage = JSON.parse(frame.body);
        console.log('[ChatService] 📩 Received message:', msg);
        this.messages$.next(msg);
      } catch (err) {
        console.error('[ChatService] ❌ Invalid message payload:', frame.body);
      }
    });

    // ✏️ Typing
    this.stompClient.subscribe('/user/queue/typing', (frame: IMessage) => {
      try {
        const event = JSON.parse(frame.body);
        console.log('[ChatService] ✏️ Typing event received:', event);
        this.typing$.next(event);
      } catch (err) {
        console.error('[ChatService] ❌ Invalid typing event:', frame.body);
      }
    });
  }

  /* =========================================================
   * ✉️ Safe Message Sending
   * ========================================================= */
  sendMessage(senderId: number, receiverId: number, message: string): void {
    const msg: ChatMessage = {
      senderId,
      receiverId,
      message,
      timestamp: new Date().toISOString(),
      status: 'SENT',
    };

    if (!this.isConnected()) {
      console.warn('[ChatService] ⚠️ Not connected — queueing message');
      this.pendingMessages.push(msg);
      this.scheduleReconnect(senderId);
      return;
    }

    this.publishMessage(msg);
  }

  sendFileMessage(senderId: number, receiverId: number, fileUrl: string, fileName: string): void {
    const msg: ChatMessage = {
      senderId,
      receiverId,
      fileUrl,
      fileName,
      timestamp: new Date().toISOString(),
      status: 'SENT',
    };

    if (!this.isConnected()) {
      console.warn('[ChatService] ⚠️ Not connected — queueing file message');
      this.pendingMessages.push(msg);
      this.scheduleReconnect(senderId);
      return;
    }

    this.publishMessage(msg);
  }

  /** 🔒 Publish with connection guard */
  private publishMessage(msg: ChatMessage): void {
    if (!this.stompClient || !this.stompClient.connected) {
      console.error('[ChatService] ❌ No active connection, queueing message');
      this.pendingMessages.push(msg);
      return;
    }

    try {
      this.stompClient.publish({
        destination: '/app/chat.send',
        body: JSON.stringify(msg),
      });
      console.log('[ChatService] 🚀 Message sent:', msg);
    } catch (err) {
      console.error('[ChatService] ❌ Publish failed, requeueing:', err);
      this.pendingMessages.push(msg);
    }
  }

  /** ♻️ Flush queued messages after reconnect */
  private flushPendingMessages(): void {
    if (!this.isConnected() || this.pendingMessages.length === 0) return;

    console.log(`[ChatService] 🔄 Flushing ${this.pendingMessages.length} queued messages`);
    for (const msg of this.pendingMessages) {
      this.publishMessage(msg);
    }
    this.pendingMessages = [];
  }

  /* =========================================================
   * 💭 Typing Notifications
   * ========================================================= */
  sendTyping(senderId: number, receiverId: number): void {
    if (!this.isConnected()) return;

    const payload = {fromId: senderId, toId: receiverId};
    try {
      this.stompClient?.publish({
        destination: '/app/chat.typing',
        body: JSON.stringify(payload),
      });
      console.log('[ChatService] ✏️ Typing sent:', payload);
    } catch (err) {
      console.error('[ChatService] ❌ Failed to send typing event:', err);
    }
  }

  getTypingStream(): Observable<{ fromId: number; toId: number }> {
    return this.typing$.asObservable();
  }

  getOnlineStatus(): Observable<{ userId: number; online: boolean } | null> {
    return this.onlineStatus$.asObservable();
  }

  /* =========================================================
   * Helpers
   * ========================================================= */
  isConnected(): boolean {
    return !!this.stompClient?.connected;
  }

  getConnectionStatus(): Observable<boolean> {
    return this.connection$;
  }

  getMessages(): Observable<ChatMessage> {
    return this.messagesStream$;
  }

  /* =========================================================
   * 🔌 Disconnect / Cleanup
   * ========================================================= */
  disconnect(): void {
    if (this.stompClient && this.stompClient.active) {
      console.log('[ChatService] 🔴 Disconnecting STOMP...');
      this.stompClient.deactivate();
    }
    this.connectionState$.next(false);
    this.onlineStatus$.next(null);
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
