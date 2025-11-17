/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * ============================================================
 * 🧠 SmartChat Realtime Presence Service (Final - Corrected)
 * ------------------------------------------------------------
 * Fixes included:
 *   ✔ remove token from SockJS URL
 *   ✔ use resolveWebSocketUrl() without DI
 *   ✔ pass JWT only as STOMP CONNECT headers
 *   ✔ use SockJS(url) without forcing transports
 *   ✔ stable reconnect & presence snapshot flow
 * ============================================================
 */

import {Injectable, NgZone, OnDestroy} from '@angular/core';
import {BehaviorSubject, Observable, Subscription, timer} from 'rxjs';
import {Client, IMessage, StompHeaders} from '@stomp/stompjs';
import {environment} from '../../../../environments/environment';
import {resolveWebSocketUrl} from '../../../utils/socket-url';
import {AuthService} from '../../auth/auth.service';
import SockJS from 'sockjs-client';
import {MatchedContact} from '../../contacts/contact.service';

@Injectable({providedIn: 'root'})
export class PresenceService implements OnDestroy {
  /* 🛰 STOMP client */
  private client?: Client;

  /* 🧩 State flags */
  private connected = false;
  private reconnecting = false;
  private reconnectAttempts = 0;
  private readonly maxReconnectDelay = 15000;
  private activeUserId?: string;

  /* 🔄 Async connect tracking */
  private connectPromise: Promise<void> | null = null;
  private connectResolve: (() => void) | null = null;

  /* 📡 Presence Map */
  private presenceMap$ = new BehaviorSubject<Map<string, any>>(new Map());
  readonly presenceStream$: Observable<Map<string, any>> =
    this.presenceMap$.asObservable();

  /* 🔌 Connection state */
  private connectionState$ = new BehaviorSubject<boolean>(false);
  readonly connection$: Observable<boolean> = this.connectionState$.asObservable();

  /* 🫀 Heartbeat */
  private heartbeatTimer?: Subscription;
  private readonly heartbeatIntervalMs = 15000;

  constructor(
    private readonly zone: NgZone,
    private readonly authService: AuthService
  ) {
  }

  /* =========================================================
   * 🚀 Connect to STOMP Broker (Corrected)
   * ========================================================= */
  async connect(userId?: string): Promise<void> {
    if (this.connected) {
      console.info('[PresenceService] ⚙️ Already connected — skip');
      return;
    }

    this.activeUserId = userId ?? this.activeUserId;

    // ❗ DO NOT ATTACH TOKEN TO URL
    const socketUrl = resolveWebSocketUrl();

    console.info('[PresenceService] 🌐 Using WS URL:', socketUrl);
    console.info('[PresenceService] 🔌 Connecting user:', this.activeUserId);

    /* 🔥 Clean up any previous client before reconnect */
    if (this.client?.active) {
      try {
        await this.client.deactivate();
      } catch (err) {
        console.warn('[PresenceService] ⚠️ Error during deactivate:', err);
      }
      this.client = undefined;
      await new Promise((r) => setTimeout(r, 250));
    }

    /* 🔐 STOMP CONNECT headers */
    const token = this.authService.getToken() || '';
    const connectHeaders: StompHeaders = {
      Authorization: `Bearer ${token}`,
    };

    this.connectPromise = new Promise<void>((resolve) => {
      this.connectResolve = resolve;
    });

    /* 🛰 Create STOMP client (IMPORTANT CHANGES BELOW) */
    this.client = new Client({
      // ✔ Clean SockJS instance
      webSocketFactory: () => new SockJS(socketUrl),

      // Custom reconnect — disable stompjs internal reconnect
      reconnectDelay: 0,

      heartbeatIncoming: 5000,
      heartbeatOutgoing: 5000,

      connectHeaders,

      debug: (msg) => {
        if (environment.enableDebugLogs && !environment.production) {
          console.debug('[STOMP-Presence]', msg);
        }
      },

      onConnect: () => this.zone.run(() => this.onConnect(this.activeUserId)),

      onStompError: (frame) =>
        this.zone.run(() => {
          console.error(
            '[PresenceService] ❌ STOMP Error:',
            frame.headers?.['message'],
            frame.body
          );
          this.connectionState$.next(false);
          this.scheduleReconnect(this.activeUserId);
        }),

      onWebSocketClose: (evt) =>
        this.zone.run(() => {
          console.warn('[PresenceService] ⚠️ WebSocket closed:', evt);
          this.connectionState$.next(false);
          this.scheduleReconnect(this.activeUserId);
        }),
    });

    console.log('[PresenceService] 🟡 Activating STOMP client...');
    this.client.activate();

    try {
      await this.connectPromise; // wait until connected
    } catch {
      /* errors handled inside callbacks */
    }
  }

  /* =========================================================
   * 🎉 On Successful Connection
   * ========================================================= */
  private onConnect(userId?: string): void {
    this.connected = true;
    this.reconnecting = false;
    this.reconnectAttempts = 0;
    this.connectionState$.next(true);

    console.log('[PresenceService] ✅ STOMP Connected');

    // 1) Subscribe BEFORE requesting snapshot so we don't miss it
    // Global presence topic
    this.client?.subscribe('/topic/presence', (msg: IMessage) => {
      try {
        this.handlePresenceUpdate(JSON.parse(msg.body));
      } catch (err) {
        console.warn('[PresenceService] ⚠️ Failed to parse /topic/presence', err);
      }
    });

    // Per-user queue (server uses convertAndSendToUser(..., "/queue/presence", ...))
    this.client?.subscribe('/user/queue/presence', (msg: IMessage) => {
      try {
        const payload = JSON.parse(msg.body);

        // support both direct snapshot payload or wrapped messages with type
        if (payload?.type === 'SNAPSHOT' && payload?.users) {
          this.handleSnapshot(payload.users);
        } else if (payload?.type === 'PING_ACK') {
          // optional: handle ping ack if needed
        } else if (payload?.users) {
          // defensive: sometimes backend might send snapshot structure directly
          this.handleSnapshot(payload.users);
        } else {
          // one-off presence update delivered to user queue
          this.handlePresenceUpdate(payload);
        }
      } catch (err) {
        console.warn('[PresenceService] ⚠️ Failed to parse /user/queue/presence', err);
      }
    });

    // 2) Now request a snapshot from the server
    this.connectResolve?.();
    this.requestSnapshot();

    // 3) Start heartbeat
    const active = userId ?? this.activeUserId;
    if (active) this.startPresenceHeartbeat(String(active));
  }

  /* =========================================================
   * 🫀 Heartbeat (Presence Ping)
   * ========================================================= */
  private startPresenceHeartbeat(userId: string): void {
    this.stopPresenceHeartbeat();

    this.heartbeatTimer = timer(0, this.heartbeatIntervalMs).subscribe(() => {
      if (!this.client?.connected) return;

      try {
        this.client.publish({
          destination: '/app/presence.ping', // <-- dot version matches backend MessageMapping
          body: JSON.stringify({userId}),
        });
      } catch (err) {
        console.warn('[PresenceService] ⚠️ Heartbeat failed:', err);
      }
    });

    console.log('[PresenceService] 🕒 Heartbeat active');
  }

  private stopPresenceHeartbeat(): void {
    this.heartbeatTimer?.unsubscribe();
    this.heartbeatTimer = undefined;
  }

  /* =========================================================
   * 🔁 Reconnect Logic
   * ========================================================= */
  private scheduleReconnect(userId?: string): void {
    if (this.reconnecting) return;

    this.reconnecting = true;
    this.reconnectAttempts++;

    const delay = Math.min(
      2000 * this.reconnectAttempts,
      this.maxReconnectDelay
    );

    console.warn(
      `[PresenceService] 🔄 Reconnecting in ${(delay / 1000).toFixed(1)}s`
    );

    timer(delay).subscribe(() => {
      this.connect(userId).catch(() => {
        /* swallow — next schedule will handle */
      });
    });
  }

  /* =========================================================
   * 📌 Presence Handlers
   * ========================================================= */
  private handlePresenceUpdate(update: any): void {
    const map = new Map(this.presenceMap$.value);
    map.set(update.userId, {
      online: update.online,
      lastSeen: update.lastSeen ? new Date(update.lastSeen) : null,
    });
    this.presenceMap$.next(map);
  }

  private handleSnapshot(snapshot: Record<string, any>): void {
    const map = new Map<string, any>();
    Object.entries(snapshot).forEach(([userId, state]) => {
      map.set(userId, {
        online: state.online ?? false,
        lastSeen: state.lastSeen ? new Date(state.lastSeen) : null,
      });
    });
    this.presenceMap$.next(map);
  }

  /* =========================================================
   * 📡 Request Snapshot
   * ========================================================= */
  private requestSnapshot(): void {
    if (!this.client?.connected) return;

    this.client.publish({
      destination: '/app/presence/request',
      body: '', // server doesn't require a body for snapshot
    });

    console.log('[PresenceService] 📡 Snapshot request sent');
  }


  /* =========================================================
   * 🧹 Cleanup
   * ========================================================= */
  disconnect(): void {
    try {
      this.client?.deactivate();
    } catch {
      /* ignore */
    }
    this.stopPresenceHeartbeat();
    this.connected = false;
    this.connectionState$.next(false);
    this.connectPromise = null;
    this.connectResolve = null;
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  /* =========================================================
   * 🔧 Helper APIs (unchanged)
   * ========================================================= */
  getPresenceStream(): Observable<Map<string, any>> {
    return this.presenceStream$;
  }

  mergeWithContacts(contacts: MatchedContact[]): MatchedContact[] {
    const presence = this.presenceMap$.value;
    return contacts.map((c) => {
      const state = presence.get(String(c.matchedUserId ?? c.contactId));
      return {
        ...c,
        online: state?.online ?? false,
        lastSeen: state?.lastSeen ?? null,
      };
    });
  }

  isUserOnline(userId: string): boolean {
    return this.presenceMap$.value.get(String(userId))?.online ?? false;
  }

  getLastSeen(userId: string): Date | null {
    return this.presenceMap$.value.get(String(userId))?.lastSeen ?? null;
  }
}
