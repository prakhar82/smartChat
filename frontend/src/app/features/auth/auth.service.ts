/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable, Injector} from '@angular/core';
import {Router} from '@angular/router';
import {HttpClient} from '@angular/common/http';
import {Observable, of, switchMap, throwError} from 'rxjs';
import {tap} from 'rxjs/operators';
import {environment} from '../../../environments/environment.prod';
import {LoggerService} from '../../core/logger.service';

/**
 * ==========================================================
 * 🔐 AuthService
 * ----------------------------------------------------------
 * Handles:
 * - Authentication (login/register/logout)
 * - Token management (store, refresh, validate)
 * - Session persistence
 * ==========================================================
 */

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
  auth_token: string;
  refresh_token?: string;
  user_id: number;
  first_name?: string;
  last_name?: string;
  email?: string;
  message?: string;
}

@Injectable({providedIn: 'root'})
export class AuthService {
  private readonly API = `${environment.apiUrl}/auth`;

  private readonly STORAGE_KEYS = {
    token: 'auth_token',
    refresh: 'refresh_token',
    firstName: 'first_name',
    userId: 'user_id',
  };

  constructor(
    private http: HttpClient,
    private injector: Injector,
    private logger: LoggerService,
    private router: Router
  ) {
  }

  /** ✅ Check if logged in and token valid */
  isLoggedIn(): boolean {
    const token = this.getToken();
    const logged = !!token && !this.isTokenExpired(token);
    this.logger.debug('[AuthService]', `isLoggedIn → ${logged}`);
    return logged;
  }

  /** ✅ Client-side JWT expiry check */
  isTokenExpired(token?: string): boolean {
    try {
      const jwt = token || this.getToken();
      if (!jwt) return true;
      const payload = JSON.parse(atob(jwt.split('.')[1]));
      const exp = payload.exp * 1000;
      return Date.now() > exp;
    } catch {
      return true;
    }
  }

  /** 🔐 Login */
  login(mobileNumber: string, password: string): Observable<AuthResponse> {
    this.logger.info('[AuthService]', `🔐 Login attempt for ${mobileNumber}`);

    return this.http
      .post<AuthResponse>(`${this.API}/login`, {mobileNumber, password})
      .pipe(
        tap({
          next: async (res) => {
            if (!res?.auth_token) {
              this.logger.error('[AuthService]', '❌ Missing auth_token', res);
              return;
            }

            this.setToken(res.auth_token);
            if (res.refresh_token) this.setRefreshToken(res.refresh_token);
            this.setUserId(res.user_id);
            this.setFirstName(res.first_name || '');

            this.logger.success('[AuthService]', '✅ Login successful');

            try {
              const {ContactService} = await import('../contacts/contact.service');
              const contactService = this.injector.get(ContactService);
              contactService.getMatchedContacts(true).subscribe();
            } catch (e) {
              this.logger.warn('[AuthService]', '⚠️ Failed to lazy-load ContactService', e);
            }
          },
          error: (err) => {
            this.logger.error('[AuthService]', '❌ Login failed', err);
          },
        })
      );
  }

  /** 📝 Register */
  register(req: RegisterRequest): Observable<any> {
    return this.http.post<any>(`${this.API}/register`, req);
  }

  /** 🚪 Logout */
  logout(): void {
    this.logger.warn('[AuthService]', '🚪 Logging out');
    localStorage.clear();
    this.router.navigate(['/auth/login']);
  }

  /** 🔁 Refresh Token */
  refreshToken(): Observable<AuthResponse> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      this.logger.warn('[AuthService]', '⚠️ No refresh token available');
      this.logout();
      return new Observable<AuthResponse>((observer) => observer.complete());
    }

    return this.http
      .post<AuthResponse>(`${this.API}/refresh`, {
        refreshToken,               // camelCase
        refresh_token: refreshToken // snake_case (backend compatibility)
      })
      .pipe(
        tap({
          next: (res) => {
            if (res?.auth_token) {
              this.setToken(res.auth_token);
              if (res.refresh_token) this.setRefreshToken(res.refresh_token);
              this.logger.success('[AuthService]', '✅ Token refreshed');
            } else {
              this.logger.error('[AuthService]', '❌ Missing auth_token in refresh');
              this.logout();
            }
          },
          error: (err) => {
            this.logger.error('[AuthService]', '❌ Token refresh failed', err);
            this.logout();
          },
        }),

        // keep your existing return behaviour
        switchMap((res) => {
          if (res?.auth_token) {
            return of(res);
          }
          return throwError(() => new Error('No token returned from refresh'));
        })
      );
  }


  // 🧠 Storage Helpers
  getToken(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.token);
  }

  setToken(t: string): void {
    localStorage.setItem(this.STORAGE_KEYS.token, t);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.refresh);
  }

  setRefreshToken(t: string): void {
    localStorage.setItem(this.STORAGE_KEYS.refresh, t);
  }

  getUserId(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.userId);
  }

  setUserId(id: string | number): void {
    localStorage.setItem(this.STORAGE_KEYS.userId, String(id));
  }

  getFirstName(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.firstName);
  }

  setFirstName(name: string): void {
    localStorage.setItem(this.STORAGE_KEYS.firstName, name);
  }
}
