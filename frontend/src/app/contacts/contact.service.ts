/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

// src/app/contacts/contact.service.ts
import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';

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
}

@Injectable({providedIn: 'root'})
export class ContactService {
  private cachedContacts: MatchedContact[] = [];
  private contactsUpdated = new BehaviorSubject<void>(undefined);

  contactsUpdated$ = this.contactsUpdated.asObservable();

  constructor(private http: HttpClient) {
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
      payload.phones!.push({
        label: 'mobile',
        value: raw.phoneNormalized,
      });
    } else if (raw.phoneRaw) {
      payload.phones!.push({
        label: 'mobile',
        value: raw.phoneRaw,
      });
    }

    if (raw.email) {
      payload.emails!.push({
        label: 'home',
        value: raw.email,
      });
    }

    return payload;
  }

  // 📥 Manual/device contact sync
  syncContacts(userId: number, contacts: any[]): Observable<any> {
    const normalizedContacts = contacts.map((c) => this.transformToPayload(c));
    const payload = {
      ownerUserId: userId,
      contacts: normalizedContacts,
    };
    return this.http.post('/api/contacts/sync', payload);
  }

  // 📥 Google contact sync
  syncGoogleContacts(accessToken: string): Observable<any> {
    return this.http.post('/api/contacts/google/sync', {accessToken});
  }

  // 📤 Fetch matched contacts
  getMatchedContacts(force = false): Observable<MatchedContact[]> {
    if (!force && this.cachedContacts.length > 0) {
      return new BehaviorSubject(this.cachedContacts).asObservable();
    }

    return new Observable<MatchedContact[]>((observer) => {
      this.http.get<MatchedContact[]>('/api/contacts/matched').subscribe({
        next: (list) => {
          this.cachedContacts = list;
          observer.next(list);
          observer.complete();
        },
        error: (err) => {
          observer.error(err);
        },
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
    return this.http.post('/api/contacts/google/invite/send', {
      contactEmail,
      contactName,
    });
  }
}
