/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * ============================================================================
 *  SmartChat - UserService (Production Ready)
 * ----------------------------------------------------------------------------
 *  Handles:
 *   • Fetching and caching current user profile
 *   • Reactive user stream for all components
 *   • LocalStorage sync for userId + firstName
 *   • Error handling with automatic recovery
 * ----------------------------------------------------------------------------
 *  Works with:
 *   → Backend: AuthController (/auth/me + /auth/profile)
 *   → NGINX:   Proxies /api/auth/** → smartchat-api → smartchat-auth
 *   → Gateway: Routes /api/auth/** internally to AuthService
 * ----------------------------------------------------------------------------
 *  © 2025 SmartChat Contributors. All Rights Reserved.
 * ============================================================================
 */

import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError, tap} from 'rxjs/operators';
import {environment} from '../../../environments/environment.prod';
import {LoggerService} from '../../core/logger.service';

/** ============================================================
 *  🧩 User Model
 * ============================================================ */
export interface SmartChatUser {
  id?: number | null;
  userId?: number | null;
  firstName?: string;
  lastName?: string;
  email?: string;
  mobileNumber?: string;
  username?: string;
  token?: string;
  tokenExpires?: number;
}

/** ============================================================
 *  💼 UserService
 * ------------------------------------------------------------
 *  Provides reactive user state management and API integration.
 * ============================================================ */
@Injectable({providedIn: 'root'})
export class UserService {
  /** Cached profile in memory */
  private userProfile?: SmartChatUser;

  /** Reactive user stream for components */
  private readonly userProfile$ = new BehaviorSubject<SmartChatUser | null>(null);

  /** Expose observable for subscription */
  readonly userProfileChanges = this.userProfile$.asObservable();

  /** Base API URL (proxied via NGINX → API Gateway) */
  private readonly baseUrl = `${environment.apiUrl}/auth`;

  /** Keep currentUser for convenience */
  private readonly currentUserSubject = new BehaviorSubject<SmartChatUser | null>(null);
  readonly currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient, private logger: LoggerService) {
  }

  // ==========================================================
  // 🌐 Fetch profile from backend
  // ==========================================================
  refreshProfile(): void {
    const endpoint = `${this.baseUrl}/profile`;
    this.logger.info('UserService', `🔄 Fetching profile from: ${endpoint}`);

    this.http
      .get<SmartChatUser>(endpoint)
      .pipe(
        tap((profile) => {
          const normalized = this.normalizeProfile(profile);
          this.userProfile = normalized;
          this.userProfile$.next(normalized);
          this.currentUserSubject.next(normalized);

          localStorage.setItem('smartchat.userId', String(normalized.id || normalized.userId || ''));
          localStorage.setItem('smartchat.userName', normalized.firstName || normalized.username || '');
          this.logger.success('UserService', '✅ Profile refreshed', normalized);
        }),
        catchError((err) => {
          this.logger.warn('UserService', '❌ Failed to refresh profile', err);
          return of(null);
        })
      )
      .subscribe();
  }

  // ==========================================================
  // 🧩 Get current user profile (cached or fetch)
  // ==========================================================
  getUserProfile(): Observable<SmartChatUser> {
    if (this.userProfile) {
      this.logger.debug('UserService', '🧠 Returning cached profile');
      return of(this.userProfile);
    }

    const endpoint = `${this.baseUrl}/profile`;
    this.logger.info('UserService', `🌐 Fetching profile from API: ${endpoint}`);

    return this.http.get<SmartChatUser>(endpoint).pipe(
      tap((profile) => {
        const normalized = this.normalizeProfile(profile);
        this.userProfile = normalized;
        this.userProfile$.next(normalized);
        this.currentUserSubject.next(normalized);

        localStorage.setItem('smartchat.userId', String(normalized.id || normalized.userId || ''));
        localStorage.setItem('smartchat.userName', normalized.firstName || normalized.username || '');
        this.logger.success('UserService', '✅ Profile loaded', normalized);
      }),
      catchError((err) => {
        this.logger.warn('UserService', '❌ Failed to load profile', err);
        return of({} as SmartChatUser);
      })
    );
  }

  // ==========================================================
  // 📡 Reactive stream accessor
  // ==========================================================
  get profile$(): Observable<SmartChatUser | null> {
    return this.userProfile$.asObservable();
  }

  // ==========================================================
  // 💾 Manual profile set (e.g., post-login)
  // ==========================================================
  setUserProfile(profile: SmartChatUser): void {
    const normalized = this.normalizeProfile(profile);
    this.userProfile = normalized;
    this.userProfile$.next(normalized);
    this.currentUserSubject.next(normalized);

    localStorage.setItem('smartchat.userId', String(normalized.id || normalized.userId || ''));
    localStorage.setItem('smartchat.userName', normalized.firstName || normalized.username || '');
    this.logger.success('UserService', '💾 Profile set manually', normalized);
  }

  // ==========================================================
  // 🧹 Clear cached data
  // ==========================================================
  clearProfile(): void {
    this.logger.warn('UserService', '🧹 Clearing cached profile');
    this.userProfile = undefined;
    this.userProfile$.next(null);
    localStorage.removeItem('smartchat.userId');
    localStorage.removeItem('smartchat.userName');
  }

  // ==========================================================
  // 🧠 Utility helpers
  // ==========================================================
  getUserId(): number | null {
    const cached = this.userProfile?.id || this.userProfile?.userId;
    if (cached) return Number(cached);

    const fromLocal = localStorage.getItem('smartchat.userId');
    return fromLocal ? Number(fromLocal) : null;
  }

  /** Normalize backend response for frontend use */
  private normalizeProfile(profile: SmartChatUser): SmartChatUser {
    return {
      ...profile,
      id: profile.id ?? profile.userId ?? undefined,
      userId: profile.userId ?? profile.id ?? undefined,
      firstName: profile.firstName ?? '',
      lastName: profile.lastName ?? '',
      email: profile.email ?? '',
      mobileNumber: profile.mobileNumber ?? '',
    };
  }
}
