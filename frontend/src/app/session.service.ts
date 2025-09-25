/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, timer, Subscription } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { getJwtExpiry } from './utils/jwt.util';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private logoutTimer?: Subscription;
  private warningTimer?: Subscription;
  countdown$ = new Subject<number>();

  constructor(private router: Router, private http: HttpClient) {}

  startSessionFromToken(token: string) {
    const exp = getJwtExpiry(token);
    if (!exp) return;

    const now = Date.now();
    const expiresInSec = Math.floor((exp - now) / 1000);

    if (expiresInSec > 0) {
      this.startSession(expiresInSec);
    } else {
      this.logout();
    }
  }

  private startSession(expiresInSec: number) {
    this.clearTimers();

    const warningAt = Math.max(0, expiresInSec - 60);

    this.warningTimer = timer(warningAt * 1000).subscribe(() => {
      let countdown = 60;
      const interval = setInterval(() => {
        this.countdown$.next(countdown--);
        if (countdown < 0) clearInterval(interval);
      }, 1000);
    });

    this.logoutTimer = timer(expiresInSec * 1000).subscribe(() => this.logout());
  }

  clearTimers() {
    this.logoutTimer?.unsubscribe();
    this.warningTimer?.unsubscribe();
  }

  refreshToken() {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
      this.logout();
      return;
    }

    this.http
      .post<any>('/api/auth/refresh', { refreshToken })
      .subscribe({
        next: (res) => {
          if (res?.accessToken) {
            localStorage.setItem('accessToken', res.accessToken);
            if (res.refreshToken) {
              localStorage.setItem('refreshToken', res.refreshToken);
            }
            this.startSessionFromToken(res.accessToken);
          }
        },
        error: () => this.logout(),
      });
  }

  logout() {
    localStorage.clear();
    this.router.navigate(['/login']);
  }
}
