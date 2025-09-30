/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {tap} from 'rxjs/operators';

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
  auth_token: string;          // ✅ JWT for SmartChat
  refresh_token?: string;      // ✅ refresh token
  user_id: number;
  first_name?: string;
  last_name?: string;
  email?: string;
  message?: string;
}

@Injectable({providedIn: 'root'})
export class AuthService {
  private readonly API = '/api/auth';
  private readonly STORAGE_KEYS = {
    token: 'auth_token',
    refresh: 'refresh_token',
    firstName: 'first_name',
    userId: 'user_id',
  };

  constructor(private http: HttpClient) {
  }

  // -----------------
  // ✅ Auth state
  // -----------------
  isLoggedIn(): boolean {
    return !!localStorage.getItem(this.STORAGE_KEYS.token);
  }

  login(mobileNumber: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API}/login`, {mobileNumber, password}).pipe(
      tap((res) => {
        if (res?.auth_token) {
          this.setToken(res.auth_token);
          if (res.refresh_token) this.setRefreshToken(res.refresh_token);
          if (res.first_name) this.setFirstName(res.first_name);
          if (res.user_id) this.setUserId(res.user_id);
          console.log('[AuthService] ✅ Login successful, tokens stored');
        } else {
          console.error('[AuthService] ❌ Missing auth_token in login response');
        }
      })
    );
  }

  register(req: RegisterRequest): Observable<any> {
    return this.http.post<any>(`${this.API}/register`, req);
  }

  // -----------------
  // ✅ Token helpers
  // -----------------
  getToken(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.token);
  }

  setToken(token: string): void {
    localStorage.setItem(this.STORAGE_KEYS.token, token);
  }

  clearToken(): void {
    localStorage.removeItem(this.STORAGE_KEYS.token);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.refresh);
  }

  setRefreshToken(token: string): void {
    localStorage.setItem(this.STORAGE_KEYS.refresh, token);
  }

  getFirstName(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.firstName);
  }

  setFirstName(name: string): void {
    localStorage.setItem(this.STORAGE_KEYS.firstName, name);
  }

  getUserId(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.userId);
  }

  setUserId(uid: string | number): void {
    localStorage.setItem(this.STORAGE_KEYS.userId, String(uid));
  }

  logout(): void {
    localStorage.clear();
  }

  // -----------------
  // ✅ Google OAuth
  // -----------------
  connectWithGoogle(): void {
    window.location.href = `${this.API}/google/init`;
  }

  // Backend should normalize to "auth_token" for consistency
  getGoogleAccessToken(): Observable<{ auth_token: string }> {
    return this.http.get<{ auth_token: string }>(`${this.API}/google/token`);
  }

  // -----------------
  // ✅ Refresh token
  // -----------------
  refreshToken(): Observable<any> {
    return this.http.post<any>(`${this.API}/refresh`, {
      refresh_token: this.getRefreshToken(),
    }).pipe(
      tap((res) => {
        if (res?.auth_token) {
          this.setToken(res.auth_token);
          if (res.refresh_token) this.setRefreshToken(res.refresh_token);
          console.log('[AuthService] 🔄 Token refreshed');
        } else {
          console.error('[AuthService] ❌ No auth_token in refresh response');
          this.logout();
        }
      })
    );
  }
}
