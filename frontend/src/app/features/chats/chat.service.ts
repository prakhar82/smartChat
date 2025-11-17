/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * ============================================================
 * 💬 SmartChat Realtime Chat Service (Final - Corrected)
 * ------------------------------------------------------------
 * Fixes included:
 *   ✔ remove token from SockJS URL
 *   ✔ resolveWebSocketUrl() has no DI usage
 *   ✔ send JWT strictly in STOMP CONNECT headers
 *   ✔ correct SockJS factory
 *   ✔ stable principal propagation
 *   ✔ stable reconnect + heartbeat + presence TTL
 * ============================================================
 */

import {Injectable, OnDestroy} from '@angular/core';
import {BehaviorSubject, Observable, Subject, Subscription, timer} from 'rxjs';
import {environment} from '../../../environments/environment';
import {AuthService} from '../auth/auth.service';
import {Client, IMessage, StompHeaders} from '@stomp/stompjs';
import {Router} from '@angular/router';
import {resolveWebSocketUrl} from '../../utils/socket-url';
import SockJS from 'sockjs-client';

export interface ChatMessage {
  id?: string;
  senderId: number;
  receiverId: number;
  message?: string;
  emoji?: string;
  fileUrl?: string;
  fileName?: string;
  status?: 'PENDING' | 'SENT' | 'DELIVERED' | 'READ';
  timestamp: string;
  reactions?: string[];
}

@Injectable({providedIn: 'root'})
export class ChatService implements OnDestroy {
  private wsUrl: string;

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {
    // ❗ No token in URL — correct for SockJS info probe
    this.wsUrl = resolveWebSocketUrl();
  }

  private stompClient: Client | null = null;
  private connectPromise: Promise<void> | null = null;
  private connectResolve: (() => void) | null = null;

  private connectionState$ = new BehaviorSubject<
    'connecting' | 'connected' | 'disconnected'
  >('disconnected');

  private messages$ = new Subject<ChatMessage>();
  private typing$ = new Subject<{ fromId: number; toId: number }>();
  private delivery$ = new Subject<{ messageId: string; status: string }>();

  private pendingMessages: ChatMessage[] = [];
  private reconnectAttempts = 0;
  private readonly maxReconnectDelay = 15000;

  private heartbeatSub?: Subscription;
  private presencePingSub?: Subscription;

  /* =========================================================
   * 🚀 Connect STOMP (Corrected)
   * ========================================================= */
  async connectStomp(userId: number | string): Promise<void> {
    const token = this.authService.getToken();
    if (!token) {
      console.warn('[ChatService] ❌ Missing JWT — logging out');
      this.authService.logout();
      return;
    }

    if (this.stompClient?.active) {
      console.info('[ChatService] ⚙️ Already connected — skip');
      return;
    }

    const connectHeaders: StompHeaders = {
      Authorization: `Bearer ${token}`, // ✔ STOMP CONNECT token
      userId: String(userId),
    };

    this.connectionState$.next('connecting');

    this.connectPromise = new Promise<void>((resolve) => {
      this.connectResolve = resolve;
    });

    /* 🛰 Create STOMP client — FIXED SockJS factory */
    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(this.wsUrl),

      connectHeaders,
      reconnectDelay: 0,

      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      debug: (msg) => {
        if (environment.enableDebugLogs && !environment.production) {
          console.debug('[STOMP-Chat]', msg);
        }
      },

      onConnect: () => {
        console.info('[ChatService] ✅ STOMP CONNECTED');

        this.connectionState$.next('connected');
        this.reconnectAttempts = 0;
        this.connectResolve?.();

        this.subscribeToUserQueues();
        this.flushPendingMessages();
        this.startChatHeartbeat(userId);
        this.startPresencePing(userId);
      },

      onStompError: (frame) => {
        console.error(
          '[ChatService] ❌ STOMP error:',
          frame.headers?.['message'],
          frame.body
        );
        this.handleDisconnect(userId);
      },

      onWebSocketClose: (evt) => {
        console.warn('[ChatService] ⚠️ WebSocket closed:', evt);
        this.handleDisconnect(userId);
      },
    });

    console.info('[ChatService] 🟡 Activating STOMP client...');
    this.stompClient.activate();

    try {
      await this.connectPromise;
    } catch {
    }
  }

  /* =========================================================
   * 📬 Subscriptions
   * ========================================================= */
  private subscribeToUserQueues(): void {
    if (!this.stompClient) return;

    this.stompClient.subscribe('/user/queue/messages', (frame: IMessage) => {
      try {
        this.messages$.next(JSON.parse(frame.body));
      } catch {
      }
    });

    this.stompClient.subscribe('/user/queue/typing', (frame: IMessage) => {
      try {
        this.typing$.next(JSON.parse(frame.body));
      } catch {
      }
    });

    this.stompClient.subscribe('/user/queue/status', (frame: IMessage) => {
      try {
        this.delivery$.next(JSON.parse(frame.body));
      } catch {
      }
    });
  }

  /* =========================================================
   * 🔁 Reconnect Logic
   * ========================================================= */
  private handleDisconnect(userId: number | string): void {
    this.connectionState$.next('disconnected');
    this.stopHeartbeat();
    this.stopPresencePing();
    this.scheduleReconnect(userId);
  }

  private scheduleReconnect(userId: number | string): void {
    this.reconnectAttempts++;

    const baseDelay = Math.min(
      5000 * this.reconnectAttempts,
      this.maxReconnectDelay
    );
    const jitter = Math.random() * 2000;
    const delay = baseDelay + jitter;

    console.warn(
      `[ChatService] 🔁 Reconnecting in ${(delay / 1000).toFixed(1)}s...`
    );

    timer(delay).subscribe(() => {
      this.connectStomp(userId).catch(() => {
      });
    });
  }

  /* =========================================================
   * 🫀 Heartbeat (Chat Ping)
   * ========================================================= */
  private startChatHeartbeat(userId: number | string): void {
    this.stopHeartbeat();
    this.heartbeatSub = timer(0, 30000).subscribe(() => {
      if (!this.stompClient?.connected) return;

      try {
        this.stompClient.publish({
          destination: '/app/chat.ping',
          body: JSON.stringify({userId}),
        });
      } catch (err) {
        console.warn('[ChatService] ⚠️ Chat heartbeat failed:', err);
      }
    });

    console.log('[ChatService] 🕒 Chat heartbeat active');
  }

  private stopHeartbeat(): void {
    this.heartbeatSub?.unsubscribe();
    this.heartbeatSub = undefined;
  }

  /* =========================================================
   * 🧩 Presence TTL Ping
   * ========================================================= */
  private startPresencePing(userId: number | string): void {
    this.stopPresencePing();
    this.presencePingSub = timer(0, 15000).subscribe(() => {
      if (!this.stompClient?.connected) return;

      try {
        this.stompClient.publish({
          destination: '/app/presence.ping',
          body: JSON.stringify({userId}),
        });
      } catch (err) {
        console.warn('[ChatService] ⚠️ Presence ping failed:', err);
      }
    });

    console.log('[ChatService] 🫀 Presence TTL ping active');
  }

  private stopPresencePing(): void {
    this.presencePingSub?.unsubscribe();
    this.presencePingSub = undefined;
  }

  /* =========================================================
   * 📨 Message Handling
   * ========================================================= */
  sendMessage(senderId: number, receiverId: number, message: string): void {
    const msg: ChatMessage = {
      id: crypto.randomUUID(),
      senderId,
      receiverId,
      message,
      timestamp: new Date().toISOString(),
      status: 'PENDING',
    };

    if (!this.stompClient?.connected) {
      console.warn('[ChatService] 🕓 Queuing message (offline)');
      this.pendingMessages.push(msg);
      return;
    }

    this.publish('/app/chat.send', msg);
  }

  private async publish(destination: string, msg: any): Promise<void> {
    if (!this.stompClient) return;

    if (!this.stompClient.connected && this.connectPromise) {
      await this.connectPromise;
    }

    try {
      this.stompClient.publish({
        destination,
        body: JSON.stringify(msg),
      });
    } catch (err) {
      console.warn('[ChatService] ⚠️ Publish failed:', err);
    }
  }

  private flushPendingMessages(): void {
    if (this.pendingMessages.length === 0) return;

    console.info(
      `[ChatService] 📨 Flushing ${this.pendingMessages.length} pending messages`
    );

    this.pendingMessages.forEach((m) =>
      this.publish('/app/chat.send', m)
    );

    this.pendingMessages = [];
  }

  /* =========================================================
   * 🔄 Observables
   * ========================================================= */
  getMessages(): Observable<ChatMessage> {
    return this.messages$.asObservable();
  }

  getTypingStream(): Observable<{ fromId: number; toId: number }> {
    return this.typing$.asObservable();
  }

  getConnectionState(): Observable<
    'connecting' | 'connected' | 'disconnected'
  > {
    return this.connectionState$.asObservable();
  }

  /* =========================================================
   * 🧹 Cleanup
   * ========================================================= */
  disconnect(): void {
    this.stopHeartbeat();
    this.stopPresencePing();

    if (this.stompClient?.active) {
      try {
        this.stompClient.deactivate();
      } catch {
      }
    }

    this.connectionState$.next('disconnected');
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
