/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, forkJoin, Observable} from 'rxjs';
import {AuthService} from '../auth/auth.service'; // 👈 import added

// ✅ Payload for device/manual contact sync
export interface ContactPayload {
  contactName?: string;
  phones?: { label: string; value: string }[];
  emails?: { label: string; value: string }[];
}

// ✅ Backend response model for matched contacts
export interface MatchedContact {
  contactId: string | null;
  contactName: string;
  registered: boolean;
  canInvite: boolean;
  phones: { label: string; value: string; registered: boolean }[];
  emails: { label: string; value: string }[];
  matchedUserId?: number | null;

  // 👇 new fields for UI
  online?: boolean;
  lastMessage?: string | null;
  lastMessageTime?: string | null;
}

@Injectable({providedIn: 'root'})
export class ContactService {
  private cachedContacts: MatchedContact[] = [];
  private contactsUpdated = new BehaviorSubject<void>(undefined);

  contactsUpdated$ = this.contactsUpdated.asObservable();

  constructor(
    private http: HttpClient,
    private authService: AuthService   // 👈 inject properly
  ) {
  }

  /**
   * 🔄 Transform a "flat" contact object (legacy/mobile device format)
   * into the new structure required by backend.
   */
  private transformToPayload(raw: any): ContactPayload {
    const payload: ContactPayload = {
      contactName: raw.contactName,
      phones: [],
      emails: [],
    };

    if (raw.phoneNormalized) {
      payload.phones!.push({label: 'mobile', value: raw.phoneNormalized});
    } else if (raw.phoneRaw) {
      payload.phones!.push({label: 'mobile', value: raw.phoneRaw});
    }

    if (raw.email) {
      payload.emails!.push({label: 'home', value: raw.email});
    }

    return payload;
  }

  // 📥 Manual/device contact sync
  syncContacts(userId: number, contacts: any[]): Observable<any> {
    const normalizedContacts = contacts.map((c) => this.transformToPayload(c));
    const payload = {ownerUserId: userId, contacts: normalizedContacts};
    return this.http.post('/api/contacts/sync', payload, {
      headers: {Authorization: `Bearer ${this.authService.getToken()}`},
    });
  }

  // 📥 Google contact sync
  syncGoogleContacts(token: string): Observable<any> {
    return this.http.post(
      '/api/contacts/google/sync',
      {access_token: token}, // ✅ use snake_case to match backend
      {
        headers: {Authorization: `Bearer ${this.authService.getToken()}`},
      }
    );
  }


  // 📤 Fetch matched contacts + enrich with recent chats
  getMatchedContacts(force = true): Observable<MatchedContact[]> {
    if (!force && this.cachedContacts.length > 0) {
      return new BehaviorSubject(this.cachedContacts).asObservable();
    }

    return new Observable<MatchedContact[]>((observer) => {
      forkJoin({
        contacts: this.http.get<MatchedContact[]>('/api/contacts/matched', {
          headers: {Authorization: `Bearer ${this.authService.getToken()}`},
        }),
        recent: this.http.get<any[]>('/api/chats/recent', {
          headers: {Authorization: `Bearer ${this.authService.getToken()}`},
        }),
      }).subscribe({
        next: ({contacts, recent}) => {
          const enriched = (contacts || []).map((c) => {
            const recentChat = recent.find(
              (r) => String(r.contactId) === String(c.matchedUserId)
            );
            return {
              ...c,
              lastMessage: recentChat?.lastMessage || null,
              lastMessageTime: recentChat?.lastMessageTime || null,
            };
          });
          this.cachedContacts = enriched;
          observer.next(this.cachedContacts);
          observer.complete();
        },
        error: (err) => observer.error(err),
      });
    });
  }

  // 📦 Local cache access
  getCachedContacts(): MatchedContact[] {
    return this.cachedContacts;
  }

  setCachedContacts(list: MatchedContact[]) {
    this.cachedContacts = list;
  }

  // 🔔 Notify subscribers (e.g. ContactListComponent) to reload
  notifyContactsUpdated() {
    this.contactsUpdated.next();
  }

  // ✉️ Send invite email via backend
  sendInviteEmail(contactEmail: string, contactName: string): Observable<any> {
    return this.http.post(
      '/api/contacts/google/invite/send',
      {contactEmail, contactName},
      {
        headers: {Authorization: `Bearer ${this.authService.getToken()}`},
      }
    );
  }

  /**
   * ✅ Update online/offline status in cached contacts
   */
  updateUserStatus(userId: number, online: boolean): void {
    let updated = false;
    this.cachedContacts = this.cachedContacts.map((c) => {
      if (c.matchedUserId === userId) {
        updated = true;
        return {...c, online};
      }
      return c;
    });

    if (updated) {
      console.log('[ContactService] 👤 Updated user status:', {userId, online});
      this.notifyContactsUpdated();
    }
  }
}
