/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {BehaviorSubject, catchError, map, Observable, switchMap, tap, throwError,} from 'rxjs';
import {AuthService} from '../auth/auth.service';
import {environment} from '../../../environments/environment.prod';


export type ContactSource = 'GOOGLE' | 'PHONE' | 'APP';

export interface ContactPayload {
  contactName?: string;
  phones?: { label: string; value: string }[];
  emails?: { label: string; value: string }[];
}

export interface MatchedContact {
  id?: number;
  contactId: number;
  matchedUserId?: number;
  contactName: string;
  phones: { label: string; value: string; registered: boolean }[];
  emails: { label: string; value: string }[];
  registered: boolean;
  canInvite: boolean;
  avatarUrl?: string;
  online?: boolean;
  lastMessage?: string | null;
  lastMessageTime?: string | null;
  source?: ContactSource;
  lastSeen?: Date | null;
  lastSyncedAt?: string;
}

@Injectable({providedIn: 'root'})
export class ContactService {
  private contactsSubject = new BehaviorSubject<MatchedContact[]>([]);
  readonly contacts$ = this.contactsSubject.asObservable();

  private cachedContacts: MatchedContact[] = [];
  private isFetching = false;

  constructor(private http: HttpClient, private authService: AuthService) {
  }

  // ---------------------------------------------------------------------
  // 🔐 Auth Headers
  // ---------------------------------------------------------------------
  private getAuthHeaders(): HttpHeaders {
    const token = this.authService.getToken();

    return new HttpHeaders({
      'Content-Type': 'application/json',
      ...(token ? {Authorization: `Bearer ${token}`} : {}),
    });
  }

  // =====================================================================
  // 🌐 Google OAuth (Final version — correct, clean, stable)
  // =====================================================================

  /** Step 1 — Backend returns JSON `{ authUrl }` (no redirect!) */
  private fetchGoogleAuthUrl(): Observable<string> {
    return this.http
      .get<{ authUrl: string }>(
        `${environment.apiUrl}/contact/google/init-url`, // 🔥 Correct endpoint
        {headers: this.getAuthHeaders()}
      )
      .pipe(
        map((res) => res.authUrl),
        catchError((err) => {
          console.error('[ContactService] ❌ OAuth URL error', err);
          return throwError(() => err);
        })
      );
  }

  /** Step 2 — Open popup and wait until closed */
  private openOAuthPopup(authUrl: string): Observable<void> {
    return new Observable<void>((observer) => {
      const popup = window.open(authUrl, '_blank', 'width=520,height=650');

      if (!popup) {
        observer.error(new Error('Popup blocked by browser'));
        return;
      }

      const interval = setInterval(() => {
        if (popup.closed) {
          clearInterval(interval);
          observer.next();
          observer.complete();
        }
      }, 500);
    });
  }

  /** Step 3 — Public sync method */
  syncGoogleContacts(): Observable<MatchedContact[]> {
    console.log('[ContactService] 🔁 Google sync initiated');

    return this.http
      .get<{ accessToken?: string | null }>(
        `${environment.apiUrl}/contact/google/token`,
        {headers: this.getAuthHeaders()}
      )
      .pipe(
        switchMap((res) => {
          if (res?.accessToken) {
            console.log('[ContactService] 🔑 Token present → syncing directly');
            return this.syncGoogleData();
          }

          console.warn('[ContactService] ⚠ No token → popup OAuth needed');
          return this.fetchGoogleAuthUrl().pipe(
            switchMap((authUrl) => this.openOAuthPopup(authUrl)),
            switchMap(() => this.syncGoogleData())
          );
        })
      );
  }

  /** Step 4 — Actual Google sync after popup */
  private syncGoogleData(): Observable<MatchedContact[]> {
    return this.http
      .post(
        `${environment.apiUrl}/contact/google/sync`,
        {},
        {headers: this.getAuthHeaders()}
      )
      .pipe(
        switchMap(() => this.getMatchedContacts(true)),
        tap((list) => this.setCachedContacts(list))
      );
  }

  // =====================================================================
  // 📱 Device Contacts Sync
  // =====================================================================
  syncContacts(userId: number, contacts: any[]): Observable<any> {
    const normalized = contacts.map((c) => this.transformToPayload(c));
    const payload = {ownerUserId: userId, contacts: normalized};

    return this.http
      .post(`${environment.apiUrl}/contact/sync`, payload, {
        headers: this.getAuthHeaders(),
      })
      .pipe(
        tap(() => console.log('[ContactService] ✔ Device contacts synced')),
        catchError((err) => throwError(() => err))
      );
  }

  // =====================================================================
  // 📥 Fetch Matched Contacts
  // =====================================================================
  getMatchedContacts(forceRefresh = false): Observable<MatchedContact[]> {
    if (this.isFetching && !forceRefresh) return this.contacts$;

    this.isFetching = true;

    return this.http
      .get<MatchedContact[]>(`${environment.apiUrl}/contact/matched`, {
        headers: this.getAuthHeaders(),
      })
      .pipe(
        tap((list) => {
          this.cachedContacts = [...list];
          this.contactsSubject.next([...list]);
          this.isFetching = false;
        }),
        catchError((err) => {
          this.isFetching = false;
          return throwError(() => err);
        })
      );
  }

  // =====================================================================
  // ✉ Email Invite
  // =====================================================================
  sendInviteEmail(contactEmail: string, contactName: string): Observable<any> {
    return this.http
      .post(
        `${environment.apiUrl}/contact/google/invite/send`,
        {contactEmail, contactName},
        {headers: this.getAuthHeaders()}
      )
      .pipe(catchError((err) => throwError(() => err)));
  }

  // =====================================================================
  // 🧩 Helper: Normalize raw device contacts
  // =====================================================================
  private transformToPayload(raw: any): ContactPayload {
    const payload: ContactPayload = {
      contactName: raw.contactName,
      phones: [],
      emails: [],
    };

    if (raw.phoneNormalized)
      payload.phones!.push({label: 'mobile', value: raw.phoneNormalized});
    else if (raw.phoneRaw)
      payload.phones!.push({label: 'mobile', value: raw.phoneRaw});

    if (raw.email)
      payload.emails!.push({label: 'home', value: raw.email});

    return payload;
  }

  // =====================================================================
  // 📦 Cache Helpers
  // =====================================================================
  getCachedContacts() {
    return [...this.cachedContacts];
  }

  setCachedContacts(list: MatchedContact[]) {
    this.cachedContacts = [...list];
    this.contactsSubject.next([...list]);
  }

  notifyContactsUpdated() {
    this.contactsSubject.next([...this.cachedContacts]);
  }
}
