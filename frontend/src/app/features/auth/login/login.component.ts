/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component} from '@angular/core';
import {Router} from '@angular/router';
import {FormsModule, NgForm} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {AuthService} from '../auth.service';
import {UserService} from '../user.service';
import {ContactService} from '../../contacts/contact.service';
import {LoggerService} from '../../../core/logger.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  mobile = '';
  password = '';
  loading = false;

  constructor(
    private auth: AuthService,
    private router: Router,
    private contactService: ContactService,
    private userService: UserService,
    private logger: LoggerService
  ) {
  }

  onLogin(form?: NgForm): void {
    this.logger.debug('LoginComponent', 'onLogin triggered');
    if (form && form.invalid) {
      this.logger.warn('LoginComponent', 'Invalid form submission');
      return;
    }

    if (!this.mobile || !this.password) {
      this.logger.warn('LoginComponent', 'Missing credentials');
      alert('Please enter mobile and password.');
      return;
    }

    this.loading = true;
    this.logger.info('LoginComponent', 'Sending login request');

    this.auth.login(this.mobile, this.password).subscribe({
      next: () => {
        this.logger.success('LoginComponent', 'Login successful');
        this.loading = false;
        this.userService.refreshProfile();
        this.router.navigate(['/chats']).then(() => {
          this.contactService.notifyContactsUpdated();
        });
      },
      error: (err) => {
        this.logger.error('LoginComponent', 'Login failed', err);
        this.loading = false;
        alert(err?.error?.message || 'Login failed. Please try again.');
      }
    });
  }
}
