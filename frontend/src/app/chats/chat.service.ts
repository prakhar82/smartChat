/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, Observable, Subject} from 'rxjs';
import {environment} from '../../environments/environment';
import {AuthService} from '../auth/auth.service';

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
}

@Injectable({providedIn: 'root'})
export class ChatService implements OnDestroy {
  private socket: WebSocket | null = null;
  private readonly wsUrl = `${environment.apiUrl.replace('/api', '')}/ws-chat`;

  private connectionState$ = new BehaviorSubject<boolean>(false);
  private messages$ = new Subject<ChatMessage>();

  /** 🔌 Expose observables */
  connection$: Observable<boolean> = this.connectionState$.asObservable();
  messagesStream$: Observable<ChatMessage> = this.messages$.asObservable();

  constructor(private authService: AuthService) {
  }

  /* =========================================================
     🌐 WebSocket Connection
  ========================================================= */
  connectWebSocket(userId: number | string): void {
    const id = String(userId);
    const token = this.authService.getToken();
    const wsFullUrl = `${this.wsUrl}?userId=${encodeURIComponent(id)}&token=${encodeURIComponent(token || '')}`;
    console.log('[ChatService] 🌐 Connecting WebSocket:', wsFullUrl);

    this.socket = new WebSocket(wsFullUrl);

    this.socket.onopen = () => {
      console.log('[ChatService] ✅ WebSocket connected');
      this.connectionState$.next(true);
    };

    this.socket.onmessage = (event) => {
      try {
        const msg: ChatMessage = JSON.parse(event.data);
        this.messages$.next(msg);
      } catch (e) {
        console.warn('[ChatService] ⚠️ Invalid WS message:', event.data);
      }
    };

    this.socket.onclose = () => {
      console.log('[ChatService] 🔌 WebSocket closed');
      this.connectionState$.next(false);
    };

    this.socket.onerror = (e) => {
      console.error('[ChatService] ❌ WebSocket error:', e);
      this.connectionState$.next(false);
    };
  }

  disconnectWebSocket(): void {
    if (this.socket) {
      console.log('[ChatService] 🔴 Disconnecting WebSocket');
      this.socket.close();
      this.socket = null;
      this.connectionState$.next(false);
    }
  }

  /* =========================================================
     ✉️ Message Sending
  ========================================================= */
  sendMessage(senderId: number, receiverId: number, message: string): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return;
    const msg: ChatMessage = {
      senderId,
      receiverId,
      message,
      timestamp: new Date().toISOString(),
      status: 'SENT',
    };
    this.socket.send(JSON.stringify(msg));
  }

  sendFileMessage(senderId: number, receiverId: number, fileUrl: string, fileName: string): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return;
    const msg: ChatMessage = {
      senderId,
      receiverId,
      fileUrl,
      fileName,
      timestamp: new Date().toISOString(),
      status: 'SENT',
    };
    this.socket.send(JSON.stringify(msg));
  }

  sendTyping(senderId: number, receiverId: number): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return;
    const typing = {type: 'typing', senderId, receiverId};
    this.socket.send(JSON.stringify(typing));
  }

  /* =========================================================
     📡 Observables
  ========================================================= */
  getConnectionStatus(): Observable<boolean> {
    return this.connectionState$.asObservable();
  }

  getMessages(): Observable<ChatMessage> {
    return this.messages$.asObservable();
  }

  /* =========================================================
     🧹 Cleanup
  ========================================================= */
  ngOnDestroy(): void {
    this.disconnectWebSocket();
  }
}
