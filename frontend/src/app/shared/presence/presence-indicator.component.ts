/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {ChangeDetectionStrategy, Component, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Subscription} from 'rxjs';
import {PresenceService} from '../presence/presence.service';

@Component({
  selector: 'app-presence-indicator',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span
      class="presence-dot"
      [class.online]="isOnline"
      [class.offline]="!isOnline"
      [title]="isOnline ? 'Online' : 'Offline'"
    ></span>
  `,
  styleUrls: ['./presence-indicator.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PresenceIndicatorComponent implements OnInit, OnDestroy {
  /** 👤 User or contact ID to monitor presence for */
  @Input() userId!: string | number;

  /** ✅ Reactive online/offline state */
  isOnline = false;

  private sub?: Subscription;

  constructor(private readonly presenceService: PresenceService) {
  }

  ngOnInit(): void {
    if (!this.userId) return;

    // Set initial value
    this.isOnline = this.presenceService.isUserOnline(this.userId);

    // Subscribe to live updates
    this.sub = this.presenceService.getPresenceStream().subscribe((map) => {
      this.isOnline = !!map.get(String(this.userId));
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
