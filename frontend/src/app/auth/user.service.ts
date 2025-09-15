/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * UserService
 * - Holds current user profile and refreshes via /api/auth/me
 */

import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject} from 'rxjs';
import {tap} from 'rxjs/operators';

export interface UserProfile {
  id: number;
  name: string;
  mobileNumber: string;
}

@Injectable({providedIn: 'root'})
export class UserService {
  private baseUrl = '/api/auth';
  private currentUserSubject = new BehaviorSubject<UserProfile | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {
  }

  refreshProfile() {
    return this.http.get<UserProfile>(`${this.baseUrl}/me`).pipe(
      tap(profile => {
        this.currentUserSubject.next(profile);
        localStorage.setItem('smartchat.userId', String(profile.id)); // ✅ unified key
        localStorage.setItem('smartchat.userName', profile.name || '');
      })
    ).subscribe({
      next: () => {
      }, error: () => {
      }
    });
  }

  getUserId(): number | null {
    const v = localStorage.getItem('smartchat.userId'); // ✅ unified key
    return v ? Number(v) : null;
  }

}
