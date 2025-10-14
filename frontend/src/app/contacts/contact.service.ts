/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable, of, switchMap, throwError} from 'rxjs';
import {catchError, map, tap} from 'rxjs/operators';
import {HttpClient} from '@angular/common/http';
import {AuthService} from '../auth/auth.service';
import {environment} from '../../environments/environment';

export type ContactSource = 'GOOGLE' | 'PHONE' | 'APP';

export interface ContactPayload {
  contactName?: string;
  phones?: { label: string; value: string }[];
  emails?: { label: string; value: string }[];
}

export interface MatchedContact {
  /** 🆔 Internal SmartChat record ID */
  id?: number;

  /** 🔗 Contact’s local ID or system reference (always numeric) */
  contactId: number;

  /** 🔗 If this contact is linked to a registered SmartChat user */
  matchedUserId?: number;

  /** 🧍 Contact’s display name */
  contactName: string;

  /** ☎️ List of phone numbers associated with the contact */
  phones: { label: string; value: string; registered: boolean }[];

  /** 📧 Contact’s email addresses */
  emails: { label: string; value: string }[];

  /** ✅ Whether this contact is a SmartChat user */
  registered: boolean;

  /** 💌 Whether this contact can be invited */
  canInvite: boolean;

  /** 🖼️ Optional avatar URL */
  avatarUrl?: string;

  /** 🟢 Online presence status */
  online?: boolean;

  /** 💬 Latest message snippet */
  lastMessage?: string | null;

  /** ⏱️ Last message timestamp */
  lastMessageTime?: string | null;

  /** 🌍 Contact source (GOOGLE, PHONE, APP) */
  source?: ContactSource;

  lastSeen?: Date | null;

  /** 🔁 Last synced timestamp */
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

  // 🌐 OAuth URL for Google popup
  getGoogleAuthInitUrl(): string {
    const token = this.authService.getToken();
    return `${environment.apiUrl}/auth/google/init?access_token=${encodeURIComponent(token || '')}`;
  }

  // 📤 Manual device contact sync
  syncContacts(userId: number, contacts: any[]): Observable<any> {
    const normalized = contacts.map((c) => this.transformToPayload(c));
    const payload = {ownerUserId: userId, contacts: normalized};
    console.log('[ContactService] 📤 Syncing device contacts for user', userId);
    return this.http.post(`${environment.apiUrl}/contacts/sync`, payload, {
      headers: {Authorization: `Bearer ${this.authService.getToken() || ''}`},
    });
  }

  // 🔄 Google contact sync flow
  syncGoogleContacts(): Observable<MatchedContact[]> {
    const tokenUrl = `${environment.apiUrl}/auth/google/token`;
    const syncUrl = `${environment.apiUrl}/contacts/google/sync`;

    console.log('[ContactService] 🔁 Starting Google contacts sync...');

    return this.http
      .get<{ accessToken?: string | null; message?: string }>(tokenUrl, {
        headers: {Authorization: `Bearer ${this.authService.getToken() || ''}`},
      })
      .pipe(
        switchMap((res) => {
          if (res?.accessToken) {
            console.log('[ContactService] ✅ Found Google token — syncing contacts...');
            return this.http
              .post<any[]>(syncUrl, {access_token: res.accessToken}, {
                headers: {Authorization: `Bearer ${this.authService.getToken() || ''}`},
              })
              .pipe(
                switchMap(() => this.getMatchedContacts(true)),
                tap((contacts) => {
                  this.setCachedContacts(contacts);
                  console.log(`[ContactService] ✅ Google sync complete (${contacts.length})`);
                })
              );
          }

          console.warn('[ContactService] ⚠️ No valid Google token — open popup');
          const popup = window.open(this.getGoogleAuthInitUrl(), '_blank', 'width=520,height=650');

          if (!popup) {
            return throwError(() => new Error('Popup blocked. Please allow popups.'));
          }

          const poll = setInterval(() => {
            if (popup.closed) {
              clearInterval(poll);
              console.log('[ContactService] 🔁 Popup closed — retry sync');
              this.syncGoogleContacts().subscribe();
            }
          }, 1000);

          return of([] as MatchedContact[]);
        }),
        catchError((err) => {
          console.error('[ContactService] ❌ Google sync failed:', err);
          return throwError(() => err);
        })
      );
  }

  // 📥 Fetch matched contacts (auto-emits results)
  getMatchedContacts(forceRefresh = false): Observable<MatchedContact[]> {
    if (this.isFetching && !forceRefresh) {
      console.log('[ContactService] 🕓 Fetch already in progress');
      return this.contacts$;
    }

    this.isFetching = true;
    console.log('[ContactService] 🔍 Fetching matched contacts...');

    return this.http.get<MatchedContact[]>(`${environment.apiUrl}/contacts/matched`, {
      headers: {Authorization: `Bearer ${this.authService.getToken() || ''}`},
    }).pipe(
      map((contacts) => this.sortContacts(contacts)),
      tap((sorted) => {
        this.cachedContacts = [...sorted];
        this.contactsSubject.next([...sorted]);
        this.isFetching = false;
        console.log(`[ContactService] ✅ Contacts emitted: ${sorted.length}`);
      }),
      catchError((err) => {
        this.isFetching = false;
        console.error('[ContactService] ❌ Fetch failed:', err);
        return throwError(() => err);
      })
    );
  }

  // 🔹 Normalize raw contact
  private transformToPayload(raw: any): ContactPayload {
    const payload: ContactPayload = {contactName: raw.contactName, phones: [], emails: []};
    if (raw.phoneNormalized)
      payload.phones!.push({label: 'mobile', value: raw.phoneNormalized});
    else if (raw.phoneRaw)
      payload.phones!.push({label: 'mobile', value: raw.phoneRaw});
    if (raw.email)
      payload.emails!.push({label: 'home', value: raw.email});
    return payload;
  }

  // 🧠 Cache helpers
  getCachedContacts(): MatchedContact[] {
    return [...this.cachedContacts];
  }

  setCachedContacts(list: MatchedContact[]): void {
    const copy = [...list];
    this.cachedContacts = copy;
    this.contactsSubject.next(copy);
  }

  notifyContactsUpdated(): void {
    this.contactsSubject.next([...this.cachedContacts]);
  }

  // 📊 Sorting helper
  private sortContacts(list: MatchedContact[]): MatchedContact[] {
    return [...list].sort((a, b) =>
      (a.contactName || '').toLowerCase().localeCompare((b.contactName || '').toLowerCase())
    );
  }

  // ✉️ Invite user by email
  sendInviteEmail(contactEmail: string, contactName: string): Observable<any> {
    const url = `${environment.apiUrl}/contacts/google/invite/send`;
    return this.http.post(url, {contactEmail, contactName}, {
      headers: {Authorization: `Bearer ${this.authService.getToken() || ''}`},
    }).pipe(
      catchError((err) => {
        console.error('[ContactService] ❌ Invite email failed:', err);
        return throwError(() => err);
      })
    );
  }
}
