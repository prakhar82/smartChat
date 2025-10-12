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

@Injectable({providedIn: 'root'})
export class PresenceService implements OnDestroy {
  private stompClient?: Client;
  private connected = false;
  private presenceMap = new Map<string, boolean>();
  private presenceSubject = new BehaviorSubject<Map<string, boolean>>(this.presenceMap);
  private subscription?: StompSubscription;

  constructor(private readonly authService: AuthService) {
    this.initWebSocket();
  }


  /* ============================================================
   * 🔌 Establish WebSocket Connection
   * ============================================================ */
  private initWebSocket(): void {
    if (this.connected) return;

    const socketUrl = environment.wsUrl;
    const token = this.authService.getToken();

    if (!token) {
      console.warn('[PresenceService] ⚠️ No auth token found — skipping WebSocket init');
      return;
    }

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      connectHeaders: {Authorization: `Bearer ${token}`},
      debug: () => {
      }, // silence debug logs
      reconnectDelay: 5000, // auto-reconnect every 5s
    });

    this.stompClient.publish({destination: '/app/presence/request'});

    this.stompClient.subscribe('/topic/presence/snapshot', (msg) => {
      const raw = JSON.parse(msg.body) as Record<string, boolean>;
      const map = new Map<string, boolean>(Object.entries(raw));
      this.presenceMap = map;
      this.presenceSubject.next(new Map(this.presenceMap));
    });


    // ✅ Register lifecycle handlers
    this.stompClient.onConnect = () => {
      this.connected = true;
      console.log('[PresenceService] ✅ Connected to STOMP WebSocket');
      this.subscribeToPresence();

      // 🧩 Ask backend for full online snapshot
      this.requestPresenceSnapshot();
    };

    this.stompClient.onStompError = (frame) => {
      console.error('[PresenceService] ❌ STOMP error:', frame.headers['message']);
    };

    this.stompClient.onWebSocketClose = () => {
      this.connected = false;
      console.warn('[PresenceService] ⚠️ WebSocket disconnected, retrying...');
    };

    // ✅ Activate connection
    this.stompClient.activate();
  }

  /* ============================================================
   * 📡 Subscribe to presence topic
   * ============================================================ */

  /** 🔁 Request a full presence snapshot from backend */
  private requestPresenceSnapshot(): void {
    if (!this.stompClient || !this.connected) return;

    console.log('[PresenceService] 📡 Requesting presence snapshot...');
    this.stompClient.publish({destination: '/app/presence/request'});
  }

  private subscribeToPresence(): void {
    if (!this.stompClient || !this.connected) {
      console.warn('[PresenceService] ⚠️ Not connected, skipping presence subscription');
      return;
    }

    // 🟢 Subscribe to incremental updates (connect/disconnect)
    const topic = '/topic/presence';
    this.subscription = this.stompClient.subscribe(topic, (message: IMessage) => {
      try {
        const payload = JSON.parse(message.body);
        this.handlePresenceEvent(payload);
      } catch (err) {
        console.error('[PresenceService] ❌ Failed to parse presence payload:', err);
      }
    });

    // 🟣 Subscribe to full snapshot
    this.stompClient.subscribe('/topic/presence/snapshot', (msg) => {
      const raw = JSON.parse(msg.body) as Record<string, boolean>;
      const map = new Map<string, boolean>(Object.entries(raw));
      this.presenceMap = map;
      this.presenceSubject.next(new Map(this.presenceMap));
      console.log('[PresenceService] 🧾 Snapshot received:', raw);
    });

    console.log(`[PresenceService] 🟢 Subscribed to ${topic} and snapshot`);
  }


  /* ============================================================
   * 🧠 Handle presence update
   * ============================================================ */
  private handlePresenceEvent(payload: any): void {
    if (!payload?.userId) return;

    const userId = String(payload.userId);
    const status = payload.status === 'ONLINE';
    this.presenceMap.set(userId, status);
    this.presenceSubject.next(new Map(this.presenceMap));

    console.log(`[PresenceService] 👤 ${userId} → ${status ? 'ONLINE' : 'OFFLINE'}`);
  }

  /* ============================================================
   * 🟢 Presence accessors
   * ============================================================ */
  getPresenceStream() {
    return this.presenceSubject.asObservable();
  }

  isUserOnline(userId: string | number | null | undefined): boolean {
    if (!userId) return false;
    return !!this.presenceMap.get(String(userId));
  }

  /* ============================================================
   * 🚪 Clean up
   * ============================================================ */
  ngOnDestroy(): void {
    if (this.subscription) this.subscription.unsubscribe();
    if (this.stompClient) {
      this.stompClient.deactivate(); // ✅ replaces old .disconnect()
      console.log('[PresenceService] 🔴 STOMP client deactivated');
    }
  }

  private reconnect(): void {
    if (this.stompClient) {
      this.stompClient.deactivate();
    }
    this.connected = false;
    this.initWebSocket();
  }

}
