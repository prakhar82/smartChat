/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable, OnDestroy} from '@angular/core';
import {Client, IMessage, StompSubscription} from '@stomp/stompjs';
import {BehaviorSubject} from 'rxjs';
import SockJS from 'sockjs-client';
import {environment} from '../../../environments/environment';
import {AuthService} from '../../auth/auth.service';

export interface UserPresence {
  online: boolean;
  lastSeen?: Date | null;
}

@Injectable({providedIn: 'root'})
export class PresenceService implements OnDestroy {
  private stompClient?: Client;
  private connected = false;

  /** userId → { online, lastSeen } */
  private presenceMap = new Map<string, UserPresence>();
  private presenceSubject = new BehaviorSubject<Map<string, UserPresence>>(this.presenceMap);
  private subscription?: StompSubscription;

  constructor(private readonly authService: AuthService) {
    this.initWebSocket();

    // 🔧 Health check for missed reconnects
    setInterval(() => {
      if (!this.connected) {
        console.warn('[PresenceService] 🔁 Auto-retry WebSocket...');
        this.initWebSocket();
      }
    }, 10000);
  }

  /* =========================================================
   * 🔌 Establish WebSocket Connection
   * ========================================================= */
  private initWebSocket(): void {
    if (this.connected) return;

    const socketUrl =
      environment.wsUrl || `${environment.apiUrl.replace('/api', '')}/ws-chat`;
    const token = this.authService.getToken();

    if (!token) {
      console.warn('[PresenceService] ⚠️ No auth token found — skipping WebSocket init');
      setTimeout(() => this.initWebSocket(), 2000); // 🔧 Retry after token delay
      return;
    }

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      connectHeaders: {Authorization: `Bearer ${token}`},
      reconnectDelay: 2000, // 🔧 faster reconnects
      heartbeatIncoming: 5000,
      heartbeatOutgoing: 5000,
      debug: () => {
      },

      onConnect: () => {
        this.connected = true;
        console.log('[PresenceService] ✅ Connected to STOMP WebSocket');
        this.subscribeToPresence();
        this.requestPresenceSnapshot();
      },

      onDisconnect: () => {
        this.connected = false;
        console.warn('[PresenceService] ⚠️ Disconnected from STOMP');
      },

      onStompError: (frame) => {
        this.connected = false;
        console.error('[PresenceService] ❌ STOMP error:', frame.headers['message']);
      },
    });

    this.stompClient.activate();
  }

  /* =========================================================
   * 📡 Subscribe to presence topics
   * ========================================================= */
  private subscribeToPresence(): void {
    if (!this.stompClient || !this.connected) {
      console.warn('[PresenceService] ⚠️ Not connected, skipping presence subscription');
      return;
    }

    // 🟢 Incremental updates
    this.subscription = this.stompClient.subscribe('/topic/presence', (msg: IMessage) => {
      try {
        const payload = JSON.parse(msg.body);
        this.handlePresenceEvent(payload);
      } catch (err) {
        console.error('[PresenceService] ❌ Invalid presence payload:', err);
      }
    });

    // 🟣 Full snapshot
    this.stompClient.subscribe('/topic/presence/snapshot', (msg) => {
      try {
        const raw = JSON.parse(msg.body) as Record<string, { online: boolean; lastSeen?: string }>;
        const newMap = new Map<string, UserPresence>();
        for (const [userId, data] of Object.entries(raw)) {
          newMap.set(userId, {
            online: !!data.online,
            lastSeen: data.lastSeen ? new Date(data.lastSeen) : null,
          });
        }
        this.presenceMap = newMap;
        this.presenceSubject.next(new Map(this.presenceMap));
        console.log('[PresenceService] 🧾 Snapshot received:', raw);
      } catch (err) {
        console.error('[PresenceService] ❌ Failed to parse snapshot:', msg.body);
      }
    });

    console.log('[PresenceService] 🟢 Subscribed to /topic/presence + snapshot');
  }

  /* =========================================================
   * 🧠 Handle single presence update
   * ========================================================= */
  private handlePresenceEvent(payload: any): void {
    if (!payload?.userId) return;

    const userId = String(payload.userId);
    const status = !!payload.online;
    const lastSeen = payload.lastSeen ? new Date(payload.lastSeen) : null;

    // 🔧 Delay marking offline to smooth reload flicker
    if (!status) {
      setTimeout(() => {
        const current = this.presenceMap.get(userId);
        if (!current?.online) {
          this.presenceMap.set(userId, {online: false, lastSeen});
          this.presenceSubject.next(new Map(this.presenceMap));
          console.log(`[PresenceService] 🔴 ${userId} → OFFLINE`);
        }
      }, 10000);
      return;
    }

    this.presenceMap.set(userId, {online: true, lastSeen});
    this.presenceSubject.next(new Map(this.presenceMap));

    console.log(
      `[PresenceService] 👤 ${userId} → ${status ? 'ONLINE' : 'OFFLINE'} ${
        lastSeen ? `(lastSeen=${lastSeen})` : ''
      }`
    );
  }

  /** 📡 Ask backend for a full snapshot */
  private requestPresenceSnapshot(): void {
    if (!this.stompClient?.connected) return;
    console.log('[PresenceService] 📡 Requesting presence snapshot...');
    this.stompClient.publish({
      destination: '/app/presence/request',
      body: '{}',
    });
  }

  /* =========================================================
   * 🟢 Public Accessors
   * ========================================================= */
  getPresenceStream() {
    return this.presenceSubject.asObservable();
  }

  isUserOnline(userId: string | number | null | undefined): boolean {
    if (!userId) return false;
    return !!this.presenceMap.get(String(userId))?.online;
  }

  getLastSeen(userId: string | number | null | undefined): Date | null {
    if (!userId) return null;
    return this.presenceMap.get(String(userId))?.lastSeen ?? null;
  }

  /**
   * Merges live presence data into the given contact array.
   * Used by ContactList or ChatsPage to enrich UI state.
   */
  mergeWithContacts<T extends { matchedUserId?: number; contactId?: number }>(
    contacts: T[]
  ): (T & { online?: boolean; lastSeen?: Date | null })[] {
    return contacts.map((c) => {
      const id = String(c.matchedUserId ?? c.contactId);
      const presence = this.presenceMap.get(id);
      return {
        ...c,
        online: presence?.online ?? false,
        lastSeen: presence?.lastSeen ?? null,
      };
    });
  }

  /* =========================================================
   * 🧹 Cleanup
   * ========================================================= */
  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    if (this.stompClient) {
      this.stompClient.deactivate();
      console.log('[PresenceService] 🔴 STOMP client deactivated');
    }
  }
}
