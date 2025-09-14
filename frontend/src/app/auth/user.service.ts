/*
 * UserService
 * - Holds current user profile and refreshes via /api/auth/me
 */

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { tap } from 'rxjs/operators';

export interface UserProfile {
  id: number;
  name: string;
  mobileNumber: string;
}

@Injectable({ providedIn: 'root' })
export class UserService {
  private baseUrl = '/api/auth';
  private currentUserSubject = new BehaviorSubject<UserProfile | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

  refreshProfile() {
    return this.http.get<UserProfile>(`${this.baseUrl}/me`).pipe(
      tap(profile => {
        this.currentUserSubject.next(profile);
        localStorage.setItem('userId', String(profile.id));
        localStorage.setItem('userName', profile.name || '');
      })
    ).subscribe({ next: () => {}, error: () => {} });
  }

  getUserId(): number | null {
    const v = localStorage.getItem('userId');
    return v ? Number(v) : null;
  }
}
