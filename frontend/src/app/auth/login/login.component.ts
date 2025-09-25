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
import {ContactService} from '../../contacts/contact.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  mobileNumber = '';
  password = '';
  loading = false;

  constructor(
    private auth: AuthService,
    private router: Router,
    private contactService: ContactService
  ) {
  }

  login(form: NgForm) {
    if (form.invalid) return;
    this.loading = true;

    this.auth.login(this.mobileNumber, this.password).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigate(['/chats']).then(() => {
          //this.contactService.notifyContactsUpdated();
        });
      },
      error: err => {
        this.loading = false;
        console.error('Login failed', err);
        alert(err?.error?.message || 'Login failed');
      }
    });
  }
}
