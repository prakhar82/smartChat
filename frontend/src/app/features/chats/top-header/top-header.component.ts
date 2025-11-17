/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  Output,
  Renderer2,
  SimpleChanges,
} from '@angular/core';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-top-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './top-header.component.html',
  styleUrls: ['./top-header.component.scss'],
})
export class TopHeaderComponent implements OnChanges {
  /** 👤 User’s display name */
  @Input() firstName: string = 'User';

  /** 🌗 Theme mode (dark/light) */
  @Input() isDarkTheme = false;

  /** 🔌 Connection state (online/offline/reconnecting) */
  @Input() connectionState: 'online' | 'offline' | 'reconnecting' = 'online';

  /** 🟢 User presence status */
  @Input() userStatus: 'available' | 'away' | 'busy' = 'available';

  /** 🌙 Emits when theme is toggled */
  @Output() toggleTheme = new EventEmitter<void>();

  /** UI state flags */
  statusPopoverOpen = false;
  badgeTooltip = 'Click to change status';
  private lastConnectionState: 'online' | 'offline' | 'reconnecting' = 'online';
  private longPressTimeout: any;
  private longPressActive = false;

  constructor(private renderer: Renderer2, private el: ElementRef) {
  }

  /* =========================================================
   * 🔄 Detect connection state changes (online/offline/reconnecting)
   * ========================================================= */
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['connectionState']) {
      const prev = this.lastConnectionState;
      const current = this.connectionState;

      // Trigger pulse when reconnecting → online
      if ((prev === 'offline' || prev === 'reconnecting') && current === 'online') {
        this.triggerConnectionPulse();
      }

      // Handle reconnect swirl visibility
      const avatar = this.el.nativeElement.querySelector('.avatar');
      if (avatar) {
        const existingSwirl = avatar.querySelector('.reconnect-swirl');
        if (current === 'reconnecting' && !existingSwirl) {
          const swirl = this.renderer.createElement('span');
          this.renderer.addClass(swirl, 'reconnect-swirl');
          this.renderer.appendChild(avatar, swirl);
        } else if (existingSwirl && current !== 'reconnecting') {
          this.renderer.removeChild(avatar, existingSwirl);
        }
      }

      this.lastConnectionState = current;
    }
  }

  /** ✅ Visual pulse animation on successful reconnection */
  private triggerConnectionPulse(): void {
    const avatar = this.el.nativeElement.querySelector('.avatar');
    if (!avatar) return;

    const pulse = this.renderer.createElement('span');
    this.renderer.addClass(pulse, 'connection-pulse');
    this.renderer.appendChild(avatar, pulse);

    setTimeout(() => this.renderer.removeChild(avatar, pulse), 1000);
  }

  /* =========================================================
   * 🌗 Theme Toggle
   * ========================================================= */
  onToggleTheme(): void {
    this.toggleTheme.emit();
  }

  /* =========================================================
   * 🟢 Status Popover Handling
   * ========================================================= */
  toggleStatusPopover(event?: Event): void {
    event?.stopPropagation();
    this.statusPopoverOpen = !this.statusPopoverOpen;
  }

  @HostListener('document:click')
  onClickOutside(): void {
    this.statusPopoverOpen = false;
  }

  @HostListener('document:keydown.escape')
  onEscClose(): void {
    this.statusPopoverOpen = false;
  }

  /** Change user status */
  setStatus(status: 'available' | 'away' | 'busy'): void {
    this.userStatus = status;
    this.statusPopoverOpen = false;
  }

  /* =========================================================
   * ✴️ Ripple & Long-press Effects
   * ========================================================= */
  onBadgePressStart(event: MouseEvent | TouchEvent): void {
    const target = (event.target as HTMLElement).closest('.avatar');
    if (!target) return;

    // Create ripple
    const ripple = document.createElement('span');
    ripple.classList.add('ripple');
    const rect = target.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height);
    ripple.style.width = ripple.style.height = `${size}px`;

    const clientX = 'touches' in event ? event.touches[0].clientX : event.clientX;
    const clientY = 'touches' in event ? event.touches[0].clientY : event.clientY;
    ripple.style.left = `${clientX - rect.left - size / 2}px`;
    ripple.style.top = `${clientY - rect.top - size / 2}px`;

    target.appendChild(ripple);
    setTimeout(() => ripple.remove(), 600);

    // Long press glow
    this.longPressTimeout = setTimeout(() => {
      this.longPressActive = true;
      target.classList.add('long-press-glow');
    }, 600);
  }

  onBadgePressEnd(event: MouseEvent | TouchEvent): void {
    clearTimeout(this.longPressTimeout);
    const target = (event.target as HTMLElement).closest('.avatar');
    if (target && this.longPressActive) {
      target.classList.remove('long-press-glow');
      this.longPressActive = false;
    }
  }
}
