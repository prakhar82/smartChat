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
import {tap} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {LoggerService} from '../core/logger.service';

/*
export interface UserProfile {
  id: number;
  name: string;
  mobileNumber: string;
}
*/

export interface SmartChatUser {
  id?: string | number;
  firstName?: string;
  lastName?: string;
  email?: string;
  mobileNumber?: string;
  token?: string;
}

@Injectable({providedIn: 'root'})
export class UserService {

  /** 🧭 Cached user profile */
  private userProfile?: SmartChatUser;

  /** 🔁 Reactive stream for components */
  private userProfile$ = new BehaviorSubject<SmartChatUser | null>(null);

  private readonly baseUrl = `${environment.apiUrl}/auth`;
  private readonly currentUserSubject = new BehaviorSubject<SmartChatUser | null>(null);
  readonly currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient, private logger: LoggerService) {
  }

  refreshProfile() {
    this.logger.debug('UserService', `Fetching profile from ${this.baseUrl}/me`);
    return this.http.get<SmartChatUser>(`${this.baseUrl}/me`).pipe(
      tap({
        next: (profile) => {
          this.logger.success('UserService', 'Profile refreshed', profile);
          this.currentUserSubject.next(profile);
          localStorage.setItem('smartchat.userId', String(profile.id));
          localStorage.setItem('smartchat.userName', profile.firstName || '');
        },
        error: (err) => this.logger.warn('UserService', 'Failed to refresh', err)
      })
    ).subscribe();
  }

  /* =========================================================
   * 📦 Public API
   * ========================================================= */

  /**
   * ✅ Returns cached user profile if available.
   *    Otherwise, fetches it from backend `/api/profile`.
   */
  getUserProfile(): Observable<SmartChatUser> {
    if (this.userProfile) {
      console.log('[UserService] 🧠 Returning cached profile');
      return of(this.userProfile);
    }

    console.log('[UserService] 🌐 Fetching profile from API...');
    return this.http
      .get<SmartChatUser>(`${this.baseUrl}/profile`)
      .pipe(
        tap((profile) => {
          this.userProfile = profile;
          this.userProfile$.next(profile);
          console.log('[UserService] ✅ Profile loaded:', profile.firstName);
        })
      );
  }

  /**
   * 🔄 Reactive profile observable for live updates.
   */
  get userProfileChanges(): Observable<SmartChatUser | null> {
    return this.userProfile$.asObservable();
  }

  /**
   * 🧹 Clears cached data on logout or token expiry.
   */
  clearProfile(): void {
    console.warn('[UserService] 🧹 Clearing cached profile');
    this.userProfile = undefined;
    this.userProfile$.next(null);
  }

  /**
   * 🧠 Manually set user profile (post-login or OAuth).
   */
  setUserProfile(profile: SmartChatUser): void {
    this.userProfile = profile;
    this.userProfile$.next(profile);
    console.log('[UserService] 💾 Profile set manually:', profile.firstName);
  }

  getUserId(): number | null {
    const v = localStorage.getItem('smartchat.userId');
    return v ? Number(v) : null;
  }
}
