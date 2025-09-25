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

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, CommonModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {
  user: RegisterRequest = {
    firstName: '',
    lastName: '',
    countryCode: '+91',
    mobileNumber: '',
    email: '',
    password: '',
    confirmPassword: '',
    googleToken: null,
    referralToken: ''
  };

  isLoading = false;
  loadingMessage = '';

  constructor(private auth: AuthService, private router: Router) {
  }

  register(form: NgForm) {
    if (form.invalid) return;

    if (this.user.password !== this.user.confirmPassword) {
      alert('Passwords do not match');
      return;
    }

    this.isLoading = true;
    this.loadingMessage = 'Registering...';

    this.auth.register(this.user).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/chats'], {queryParams: {showGooglePopup: 'true'}});
      },
      error: err => {
        console.error('Register failed', err);
        this.isLoading = false;
        alert(err?.error?.message || 'Registration failed');
      }
    });
  }
}
