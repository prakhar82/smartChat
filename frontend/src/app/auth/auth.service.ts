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
  message?: string;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  countryCode: string;
  mobileNumber: string;
  email: string;
  password: string;
  confirmPassword: string;
  googleToken?: string | null;
  referralToken?: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/auth';

  constructor(private http: HttpClient) {
  }

  register(model: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, model).pipe(
      tap((res) => this.setSession(res)),
      catchError((error) => {
        console.error('❌ Registration failed', error);
        return throwError(() => error);
      })
    );
  }

  login(mobileNumber: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login`, {mobileNumber, password})
      .pipe(
        tap((res) => this.setSession(res)),
        catchError((error) => {
          console.error('❌ Login failed', error);
          return throwError(() => error);
        })
      );
  }

  refreshToken(): Observable<{ accessToken: string }> {
    return this.http.post<{ accessToken: string }>(
      `${this.apiUrl}/refresh`,
      {}
    );
  }

  /**
   * ✅ Single method to save tokens + user info
   */
  setSession(res: AuthResponse) {
    if (!res) return;
    localStorage.setItem('smartchat.accessToken', res.accessToken || '');
    localStorage.setItem('smartchat.refreshToken', res.refreshToken || '');
    localStorage.setItem('smartchat.userId', res.userId?.toString() || '');
    localStorage.setItem('smartchat.firstName', res.firstName || '');
    localStorage.setItem('smartchat.lastName', res.lastName || '');
    localStorage.setItem('smartchat.email', res.email || '');
  }

  getToken(): string | null {
    return localStorage.getItem('smartchat.accessToken');
  }

  getUserId(): number {
    const uid = localStorage.getItem('smartchat.userId');
    return uid ? Number(uid) : 0;
  }

  logout(): void {
    localStorage.removeItem('smartchat.accessToken');
    localStorage.removeItem('smartchat.refreshToken');
    localStorage.removeItem('smartchat.userId');
    localStorage.removeItem('smartchat.firstName');
    localStorage.removeItem('smartchat.lastName');
    localStorage.removeItem('smartchat.email');
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  getFirstName(): string | null {
    return localStorage.getItem('smartchat.firstName');
  }
}
