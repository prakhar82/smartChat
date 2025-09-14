/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {catchError, Observable, tap, throwError} from 'rxjs';

export interface AuthResponse {
  userId: number;
  accessToken: string;
  refreshToken: string;
  firstName?: string;
  lastName?: string;
  email?: string;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  countryCode: string;
  mobileNumber: string;
  email: string;
  password: string;
  confirmPassword: string;
  googleToken?: string | null; // optional
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  // 🔑 Base API URL (replace with environments if needed)
  private apiUrl = 'http://localhost:8080/api/auth';

  constructor(private http: HttpClient) {
  }

  /**
   * Register a new user
   */
  register(model: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, model).pipe(
      tap((res) => this.storeTokens(res)),
      catchError((error) => {
        console.error('❌ Registration failed', error);
        return throwError(() => error);
      })
    );
  }

  /**
   * Login with mobile number and password
   */
  login(mobileNumber: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login`, {mobileNumber, password})
      .pipe(
        tap((res) => this.storeTokens(res)),
        catchError((error) => {
          console.error('❌ Login failed', error);
          return throwError(() => error);
        })
      );
  }

  /**
   * Refresh access token
   */
  refreshToken(): Observable<{ accessToken: string }> {
    return this.http.post<{ accessToken: string }>(
      `${this.apiUrl}/refresh`,
      {}
    );
  }

  /**
   * Store authentication tokens + user info
   */
  private storeTokens(res: AuthResponse) {
    if (res.accessToken) {
      localStorage.setItem('smartchat.accessToken', res.accessToken);
    }
    if (res.refreshToken) {
      localStorage.setItem('smartchat.refreshToken', res.refreshToken);
    }
    if (res.userId !== null && res.userId !== undefined) {
      localStorage.setItem('smartchat.userId', String(res.userId));
    }
    if (res.firstName) {
      localStorage.setItem('smartchat.firstName', res.firstName);
    }
    if (res.lastName) {
      localStorage.setItem('smartchat.lastName', res.lastName);
    }
    if (res.email) {
      localStorage.setItem('smartchat.email', res.email);
    }
  }

  /**
   * Get stored access token
   */
  getToken(): string | null {
    return localStorage.getItem('smartchat.accessToken');
  }

  /**
   * Get logged-in user ID
   */
  getUserId(): number {
    const uid = localStorage.getItem('smartchat.userId');
    return uid ? Number(uid) : 0;
  }

  /**
   * Logout user and clear tokens
   */
  logout(): void {
    localStorage.removeItem('smartchat.accessToken');
    localStorage.removeItem('smartchat.refreshToken');
    localStorage.removeItem('smartchat.userId');
    localStorage.removeItem('smartchat.firstName');
    localStorage.removeItem('smartchat.lastName');
    localStorage.removeItem('smartchat.email');
  }

  /**
   * Check if user is logged in
   */
  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  /**
   * Get cached first name (optional)
   */
  getFirstName(): string | null {
    return localStorage.getItem('smartchat.firstName');
  }
}
