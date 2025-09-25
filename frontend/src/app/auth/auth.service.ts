/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

// src/app/auth/auth.service.ts
import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, tap} from 'rxjs';

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

export interface AuthResponse {
  accessToken: string;
  refreshToken?: string;
  userId: number;
  firstName?: string;
  lastName?: string;
  email?: string;
  message?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = '/api/auth';

  constructor(private http: HttpClient) {
  }

  register(data: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, data).pipe(
      tap(res => this.setSession(res))
    );
  }

  login(mobileNumber: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, {mobileNumber, password})
      .pipe(tap(res => this.setSession(res)));
  }

  /** Save tokens + user info */
  private setSession(res: AuthResponse): void {
    if (!res) return;
    this.setToken(res.accessToken);
    if (res.refreshToken) this.setRefreshToken(res.refreshToken);
    localStorage.setItem('smartchat.userId', String(res.userId));
    if (res.firstName) localStorage.setItem('smartchat.firstName', res.firstName);
    if (res.lastName) localStorage.setItem('smartchat.lastName', res.lastName);
    if (res.email) localStorage.setItem('smartchat.email', res.email);
  }

  /** Clear session */
  logout(): void {
    this.clearToken();
    localStorage.removeItem('smartchat.userId');
    localStorage.removeItem('smartchat.firstName');
    localStorage.removeItem('smartchat.lastName');
    localStorage.removeItem('smartchat.email');
  }

  /** 🔑 Access Token helpers */
  getToken(): string | null {
    return localStorage.getItem('smartchat.accessToken');
  }

  setToken(token: string): void {
    localStorage.setItem('smartchat.accessToken', token);
  }

  clearToken(): void {
    localStorage.removeItem('smartchat.accessToken');
    localStorage.removeItem('smartchat.refreshToken');
  }

  /** 🔄 Refresh Token helpers */
  private setRefreshToken(token: string): void {
    localStorage.setItem('smartchat.refreshToken', token);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem('smartchat.refreshToken');
  }

  /** 🔄 API call to refresh tokens */
  refreshToken() {
    return this.http.post<AuthResponse>(`${this.apiUrl}/refresh-token`, {
      refreshToken: this.getRefreshToken()
    }).pipe(
      tap(res => this.setSession(res)) // update both tokens
    );
  }

  /** User info helpers */
  getUserId(): number | null {
    const id = localStorage.getItem('smartchat.userId');
    return id ? Number(id) : null;
  }

  getFirstName(): string | null {
    return localStorage.getItem('smartchat.firstName');
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }
}
