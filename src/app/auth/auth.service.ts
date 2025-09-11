/*
 * SmartChat Angular Auth Service
 * - Handles login, register, refresh, logout
 * - Stores JWT tokens in localStorage
 * - Validates token expiration
 */

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface AuthResponse {
  userId: number;
  accessToken: string;
  refreshToken: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = '/api/auth'; // use proxy or full URL based on your environment

  constructor(private http: HttpClient) {}

  login(mobileNumber: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, { mobileNumber, password })
      .pipe(tap(res => this.storeTokens(res)));
  }

  register(name: string, mobileNumber: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, { name, mobileNumber, password })
      .pipe(tap(res => this.storeTokens(res)));
  }

  refreshToken(): Observable<AuthResponse> {
    const refreshToken = this.getRefreshToken();
    return this.http.post<AuthResponse>(`${this.apiUrl}/refresh`, { refreshToken })
      .pipe(tap(res => this.storeTokens(res)));
  }

  logout(): void {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
  }

  getToken(): string | null {
    return localStorage.getItem('accessToken');
  }

  private getRefreshToken(): string | null {
    return localStorage.getItem('refreshToken');
  }

  private storeTokens(res: AuthResponse): void {
    if (res?.userId != null) localStorage.setItem('userId', String(res.userId));
    // attempt to refresh profile via UserService if available
    try { (this as any).userService?.refreshProfile(); } catch(e){}

    if (res?.accessToken) localStorage.setItem('accessToken', res.accessToken);
    if (res?.refreshToken) localStorage.setItem('refreshToken', res.refreshToken);
  }

  isLoggedIn(): boolean {
    const token = this.getToken();
    return token != null && !this.isTokenExpired(token);
  }

  autoLogin(): boolean {
    const token = this.getToken();
    if (!token) return false;
    if (this.isTokenExpired(token)) {
      this.logout();
      return false;
    }
    return true;
  }

  private isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const exp = payload.exp;
      if (!exp) return true;
      const now = Math.floor(Date.now() / 1000);
      return now > exp;
    } catch (e) {
      return true;
    }
  }
}
