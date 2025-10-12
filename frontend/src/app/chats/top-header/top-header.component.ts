/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * 🧭 TopHeaderComponent
 * ---------------------------------------------------------
 * Displays:
 *  - SmartChat logo + brand
 *  - User greeting + presence
 *  - Connection indicator (🟢 / 🔴)
 *  - Theme toggle (☀️ / 🌙)
 * Matches sidebar + footer styling.
 * ---------------------------------------------------------
 */

import {Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-top-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './top-header.component.html',
  styleUrls: ['./top-header.component.scss'],
})
export class TopHeaderComponent {
  /** 👤 User name */
  @Input() firstName: string = 'User';

  /** 🟢 Presence */
  @Input() userStatus: 'available' | 'away' = 'available';

  /** 🌗 Theme */
  @Input() isDarkTheme: boolean = false;

  /** 🔌 Connection state */
  @Input() isConnected: boolean = true;

  /** 🌞 Emit theme toggle */
  @Output() toggleTheme = new EventEmitter<void>();

  /** 🔘 Handle theme toggle */
  onToggleTheme(): void {
    console.log('[TopHeader] 🎨 Theme toggle clicked');
    this.toggleTheme.emit();
  }
}
