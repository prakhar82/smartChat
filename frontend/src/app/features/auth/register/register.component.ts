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
import {AuthService, RegisterRequest} from '../auth.service';
import {LoggerService} from '../../../core/logger.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, CommonModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {
  firstName = '';
  lastName = '';
  mobile = '';
  email = '';
  password = '';
  confirm = '';
  isLoading = false;

  constructor(private auth: AuthService, private router: Router, private logger: LoggerService) {
  }

  onRegister(form?: NgForm): void {
    this.logger.info('RegisterComponent', 'Registration initiated');

    if (form && form.invalid) {
      this.logger.warn('RegisterComponent', 'Invalid form');
      return;
    }

    if (this.password !== this.confirm) {
      this.logger.warn('RegisterComponent', 'Password mismatch');
      alert('Passwords do not match');
      return;
    }

    const payload: RegisterRequest = {
      firstName: this.firstName,
      lastName: this.lastName,
      countryCode: '+91',
      mobileNumber: this.mobile,
      email: this.email,
      password: this.password,
      confirmPassword: this.confirm,
      googleToken: null,
      referralToken: ''
    };

    this.isLoading = true;
    this.logger.debug('RegisterComponent', 'Payload', payload);

    this.auth.register(payload).subscribe({
      next: () => {
        this.logger.success('RegisterComponent', 'Registration successful');
        this.isLoading = false;
        alert('Registration successful! Please log in to continue.');
        this.router.navigate(['/login']);
      },
      error: (err) => {
        this.logger.error('RegisterComponent', 'Registration failed', err);
        this.isLoading = false;
        alert(err?.error?.message || 'Registration failed. Please try again.');
      }
    });
  }
}
