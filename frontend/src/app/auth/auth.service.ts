/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable, Injector} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {tap} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {LoggerService} from '../core/logger.service';

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
    userId: 'user_id'
  };

  constructor(
    private http: HttpClient,
    private injector: Injector,
    private logger: LoggerService
  ) {
  }


  isLoggedIn(): boolean {
    const logged = !!localStorage.getItem(this.STORAGE_KEYS.token);
    this.logger.debug('AuthService', `isLoggedIn -> ${logged}`);
    return logged;
  }

  /**
   * Handles user login and automatic contact cache refresh.
   */
  login(mobileNumber: string, password: string): Observable<AuthResponse> {
    this.logger.info('[AuthService]', `🔐 Login attempt for ${mobileNumber}`);

    return this.http.post<AuthResponse>(`${this.API}/login`, {mobileNumber, password}).pipe(
      tap({
        next: async (res) => {
          if (res?.auth_token) {
            this.setToken(res.auth_token);
            if (res.refresh_token) this.setRefreshToken(res.refresh_token);
            this.logger.success('[AuthService]', '✅ Login success — tokens stored', res);

            // ✅ Lazy-resolve ContactService to avoid circular dependency
            try {
              const {ContactService} = await import('../contacts/contact.service');
              const contactService = this.injector.get(ContactService);
              this.logger.info('[AuthService]', '📇 Refreshing matched contacts post-login...');

              contactService.getMatchedContacts(true).subscribe({
                next: (contacts) =>
                  this.logger.success('[AuthService]', `✅ Loaded ${contacts.length} contacts after login`),
                error: (err) =>
                  this.logger.warn('[AuthService]', `⚠️ Could not load contacts post-login: ${err.message || err}`),
              });
            } catch (e) {
              this.logger.warn('[AuthService]', '⚠️ Failed to lazy-load ContactService for refresh', e);
            }
          } else {
            this.logger.error('[AuthService]', '❌ Missing auth_token in response', res);
          }
        },
        error: (err) => {
          this.logger.error('[AuthService]', '❌ Login failed', err);
        },
      })
    );
  }

  register(req: RegisterRequest): Observable<any> {
    this.logger.debug('AuthService', 'Register payload', req);
    return this.http.post<any>(`${this.API}/register`, req);
  }

  logout(): void {
    this.logger.warn('AuthService', 'User logging out');
    localStorage.clear();
  }

  refreshToken(): Observable<any> {
    this.logger.debug('AuthService', 'Refreshing token...');
    return this.http
      .post<any>(`${this.API}/refresh`, {refresh_token: this.getRefreshToken()})
      .pipe(
        tap({
          next: (res) => {
            if (res?.auth_token) {
              this.setToken(res.auth_token);
              this.logger.success('AuthService', 'Token refreshed');
            } else {
              this.logger.error('AuthService', 'No auth_token in refresh response');
              this.logout();
            }
          },
          error: (err) => this.logger.error('AuthService', 'Refresh failed', err)
        })
      );
  }

  // storage helpers...
  getToken(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.token);
  }

  setToken(t: string) {
    localStorage.setItem(this.STORAGE_KEYS.token, t);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.refresh);
  }

  setRefreshToken(t: string) {
    localStorage.setItem(this.STORAGE_KEYS.refresh, t);
  }

  getUserId(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.userId);
  }

  setUserId(id: string | number) {
    localStorage.setItem(this.STORAGE_KEYS.userId, String(id));
  }

  getFirstName(): string | null {
    return localStorage.getItem(this.STORAGE_KEYS.firstName);
  }

  setFirstName(name: string): void {
    localStorage.setItem(this.STORAGE_KEYS.firstName, name);
  }

}
