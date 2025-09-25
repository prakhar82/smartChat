/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SessionService } from '../session.service';

@Component({
  selector: 'app-session-warning',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './session-warning.component.html',
  styleUrls: ['./session-warning.component.css']
})
export class SessionWarningComponent {
  @Input() visible = false;
  @Input() countdown: number | null = null;

  constructor(private session: SessionService) {}

  stayLoggedIn() {
    this.session.refreshToken();
  }

  logout() {
    this.session.logout();
  }
}
