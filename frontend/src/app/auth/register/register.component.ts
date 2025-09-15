/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule, NgForm} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {AuthService, RegisterRequest} from '../auth.service';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css'],
  standalone: true,
  imports: [CommonModule, FormsModule],
})
export class RegisterComponent {
  model: RegisterRequest = {
    firstName: '',
    lastName: '',
    countryCode: '+91',
    mobileNumber: '',
    password: '',
    confirmPassword: '',
    email: '',
    googleToken: null,
    referralToken: ''
  };


  errorMsg = '';
  loading = false;

  constructor(private auth: AuthService,
              private router: Router,
              private route: ActivatedRoute
  ) {
  }

  ngOnInit(): void {
    // ✅ Capture referral token from URL if present
    const ref = this.route.snapshot.queryParamMap.get('ref');
    if (ref) {
      this.model.referralToken = ref;
      console.log('Referral token detected:', ref);
    }
  }

  /** Simple Register Handler */
  onRegister(form: NgForm) {
    if (form.invalid) {
      this.errorMsg = '⚠️ Please fill all required fields';
      return;
    }

    if (this.model.password !== this.model.confirmPassword) {
      this.errorMsg = '⚠️ Passwords do not match';
      return;
    }

    this.loading = true;
    this.auth.register(this.model).subscribe({
      next: (res) => {
        this.loading = false;
  
        // ✅ Save tokens and user details
        this.auth.setSession(res);

        // ✅ Redirect to Sync Contacts screen
        this.router.navigate(['/sync-contacts']);
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = err.error?.message || '❌ Registration failed: Unknown error';
      }
    });
  }

}
