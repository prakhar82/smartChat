/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, EventEmitter, Input, Output} from '@angular/core';
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

  close() {
    this.closed.emit();
  }
}
