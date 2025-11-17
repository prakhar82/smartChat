/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, EventEmitter, HostListener, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app-modal.component.html',
  styleUrls: ['./app-modal.component.css'],
})
export class AppModalComponent {
  @Input() title = '';
  @Input() show = false;
  @Output() closed = new EventEmitter<void>();

  constructor() {
    console.log('[AppModalComponent] Initialized');
  }

  /** Close modal */
  close(): void {
    console.log('[AppModalComponent] Close triggered');
    this.show = false;
    this.closed.emit();
  }

  /** Handle ESC key press globally (Angular 17 safe) */
  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: any): void {
    if (event?.key === 'Escape' && this.show) {
      console.log('[AppModalComponent] ESC pressed — closing modal');
      this.close();
    }
  }

  /** Handle backdrop click */
  onBackdropClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (target.classList.contains('modal-backdrop')) {
      console.log('[AppModalComponent] Backdrop clicked');
      this.close();
    }
  }
}
