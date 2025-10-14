/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError, tap} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {LoggerService} from '../core/logger.service';

export interface SmartChatUser {
  id?: number | null;           // allow null safely
  userId?: number | null;       // allow null safely
  firstName?: string;
  lastName?: string;
  email?: string;
  mobileNumber?: string;
  username?: string;
  tokenExpires?: number;
  token?: string;
}

@Injectable({providedIn: 'root'})
export class UserService {
  /** 🧭 Cached user profile */
  private userProfile?: SmartChatUser;

  /** 🔁 Reactive stream for components */
  private readonly userProfile$ = new BehaviorSubject<SmartChatUser | null>(null);

  private readonly baseUrl = `${environment.apiUrl}/auth`;
  private readonly currentUserSubject = new BehaviorSubject<SmartChatUser | null>(null);
  readonly currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient, private logger: LoggerService) {
  }

  /* =========================================================
   * 🌐 Fetch profile from backend
   * ========================================================= */
  refreshProfile(): void {
    this.logger.debug('UserService', `Fetching profile from ${this.baseUrl}/profile`);
    this.http
      .get<SmartChatUser>(`${this.baseUrl}/profile`)
      .pipe(
        tap((profile) => {
          const normalizedProfile = this.normalizeProfile(profile);
          this.userProfile = normalizedProfile;
          this.userProfile$.next(normalizedProfile);
          this.currentUserSubject.next(normalizedProfile);

          localStorage.setItem('smartchat.userId', String(normalizedProfile.id || normalizedProfile.userId || ''));
          localStorage.setItem('smartchat.userName', normalizedProfile.firstName || normalizedProfile.username || '');
          this.logger.success('UserService', '✅ Profile refreshed', normalizedProfile);
        }),
        catchError((err) => {
          this.logger.warn('UserService', '❌ Failed to refresh profile', err);
          return of(null);
        })
      )
      .subscribe();
  }

  /* =========================================================
   * 📦 Public API
   * ========================================================= */
  getUserProfile(): Observable<SmartChatUser> {
    if (this.userProfile) {
      this.logger.debug('UserService', '🧠 Returning cached profile');
      return of(this.userProfile);
    }

    this.logger.debug('UserService', '🌐 Fetching profile from API...');
    return this.http.get<SmartChatUser>(`${this.baseUrl}/profile`).pipe(
      tap((profile) => {
        const normalizedProfile = this.normalizeProfile(profile);
        this.userProfile = normalizedProfile;
        this.userProfile$.next(normalizedProfile);
        this.currentUserSubject.next(normalizedProfile);

        localStorage.setItem('smartchat.userId', String(normalizedProfile.id || normalizedProfile.userId || ''));
        localStorage.setItem('smartchat.userName', normalizedProfile.firstName || normalizedProfile.username || '');
        this.logger.success('UserService', '✅ Profile loaded', normalizedProfile);
      }),
      catchError((err) => {
        this.logger.warn('UserService', '❌ Failed to load profile', err);
        return of({} as SmartChatUser);
      })
    );
  }

  /* =========================================================
   * 📡 Reactive stream
   * ========================================================= */
  get userProfileChanges(): Observable<SmartChatUser | null> {
    return this.userProfile$.asObservable();
  }

  /* =========================================================
   * 🧹 Clear + Reset
   * ========================================================= */
  clearProfile(): void {
    this.logger.warn('UserService', '🧹 Clearing cached profile');
    this.userProfile = undefined;
    this.userProfile$.next(null);
    localStorage.removeItem('smartchat.userId');
    localStorage.removeItem('smartchat.userName');
  }

  /* =========================================================
   * 💾 Manual set (used post-login)
   * ========================================================= */
  setUserProfile(profile: SmartChatUser): void {
    const normalizedProfile = this.normalizeProfile(profile);
    this.userProfile = normalizedProfile;
    this.userProfile$.next(normalizedProfile);
    this.currentUserSubject.next(normalizedProfile);

    localStorage.setItem('smartchat.userId', String(normalizedProfile.id || normalizedProfile.userId || ''));
    localStorage.setItem('smartchat.userName', normalizedProfile.firstName || normalizedProfile.username || '');
    this.logger.success('UserService', '💾 Profile set manually', normalizedProfile);
  }

  /* =========================================================
   * 🧠 Utilities
   * ========================================================= */
  getUserId(): number | null {
    const cached = this.userProfile?.id || this.userProfile?.userId;
    if (cached) return Number(cached);

    const fromLocal = localStorage.getItem('smartchat.userId');
    return fromLocal ? Number(fromLocal) : null;
  }

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
