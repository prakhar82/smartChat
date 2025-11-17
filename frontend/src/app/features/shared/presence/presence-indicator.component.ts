/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * 💬 PresenceIndicatorComponent (Final Version)
 * ---------------------------------------------------------
 * Displays a small reactive indicator showing whether a user
 * is online, offline, or away — updating live via PresenceService.
 * - Uses OnPush change detection for performance
 * - Accessible with ARIA labels and hover tooltips
 * - Supports dark mode and reduced motion
 * ---------------------------------------------------------
 */

import {ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy, OnInit,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Subscription} from 'rxjs';
import {PresenceService} from './presence.service';

@Component({
  selector: 'app-presence-indicator',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span
      class="presence-dot"
      [class.online]="isOnline"
      [class.away]="isAway"
      [class.offline]="!isOnline && !isAway"
      [attr.aria-label]="tooltip"
      [attr.title]="tooltip"
    ></span>
  `,
  styleUrls: ['./presence-indicator.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PresenceIndicatorComponent implements OnInit, OnDestroy {
  /** 👤 ID of the user/contact whose presence is being tracked */
  @Input() userId!: string | number;

  /** ✅ Online/offline flags */
  isOnline = false;
  isAway: null | boolean = false;

  /** 🕒 Last seen timestamp */
  lastSeen: Date | null = null;

  /** 💬 Tooltip text */
  tooltip = 'Unknown';

  /** 🧹 Internal subscription */
  private sub?: Subscription;

  constructor(
    private readonly presenceService: PresenceService,
    private readonly cdRef: ChangeDetectorRef
  ) {
  }

  // =========================================================
  // 🚀 Lifecycle - Initialization
  // =========================================================
  ngOnInit(): void {
    if (this.userId == null) {
      console.warn('[PresenceIndicator] ⚠️ No userId provided — skipping subscription');
      return;
    }

    // Initialize immediately
    this.updatePresenceState();

    // Subscribe to live presence stream
    const presence$ =
      (this.presenceService as any).getPresenceStream?.() ??
      (this.presenceService as any).getPresenceState$?.();

    if (!presence$) {
      console.error('[PresenceIndicator] ❌ PresenceService stream not found!');
      return;
    }

    this.sub = presence$.subscribe(() => {
      this.updatePresenceState();
    });
  }

  // =========================================================
  // 🧠 Reactive state update (from PresenceService)
  // =========================================================
  private updatePresenceState(): void {
    const userKey = String(this.userId);

    const online = this.presenceService.isUserOnline(userKey);
    const lastSeen = this.presenceService.getLastSeen(userKey);

    // Optional: derive away state (idle > 5 mins)
    const isAway =
      !online &&
      lastSeen &&
      Date.now() - new Date(lastSeen).getTime() < 5 * 60 * 1000;

    if (
      this.isOnline !== online ||
      this.isAway !== isAway ||
      this.lastSeen !== lastSeen
    ) {
      this.isOnline = online;
      this.isAway = isAway;
      this.lastSeen = lastSeen;

      // Update tooltip
      if (this.isOnline) {
        this.tooltip = 'Online';
      } else if (this.isAway) {
        this.tooltip = 'Away';
      } else if (this.lastSeen) {
        this.tooltip = `Last seen ${this.lastSeen.toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
        })}`;
      } else {
        this.tooltip = 'Offline';
      }

      this.cdRef.markForCheck();
    }
  }

  // =========================================================
  // 🧹 Lifecycle - Cleanup
  // =========================================================
  ngOnDestroy(): void {
    if (this.sub) {
      this.sub.unsubscribe();
      console.log(
        `[PresenceIndicator] 🧹 Unsubscribed from presence stream (userId=${this.userId})`
      );
    }
  }
}
