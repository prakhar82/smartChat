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

// Payload for device/manual contact sync
export interface ContactPayload {
  contactName?: string;
  phoneNormalized?: string;
  phoneRaw?: string;
  email?: string;
}

// Backend response model for matched contacts
export interface MatchedContact {
  contactId: string | null;
  contactName: string;
  registered: boolean;
  canInvite: boolean;
  phones: { label: string; value: string; registered: boolean }[];
  emails: { label: string; value: string }[];
}

@Injectable({providedIn: 'root'})
export class ContactService {
  private cachedContacts: MatchedContact[] = [];
  private contactsUpdated = new BehaviorSubject<void>(undefined);

  contactsUpdated$ = this.contactsUpdated.asObservable();

  constructor(private http: HttpClient) {
  }

  //  Manual/device contact sync
  syncContacts(userId: number, contacts: ContactPayload[]): Observable<any> {
    const payload = {
      ownerUserId: userId,
      contacts: contacts,
    };
    return this.http.post('/api/contacts/sync', payload);
  }

  // Google contact sync
  syncGoogleContacts(accessToken: string): Observable<any> {
    return this.http.post('/api/contacts/google/sync', {accessToken});
  }

  // Fetch matched contacts
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

  // Local cache access
  getCachedContacts(): MatchedContact[] {
    return this.cachedContacts;
  }

  setCachedContacts(list: MatchedContact[]) {
    this.cachedContacts = list;
  }

  // Notify subscribers (e.g. ContactListComponent) to reload
  notifyContactsUpdated() {
    this.contactsUpdated.next();
  }

  // ✅ Send invite email
  sendInviteEmail(contactEmail: string, contactName: string): Observable<any> {
    return this.http.post('/api/contacts/google/invite/send', {
      contactEmail,
      contactName,
    });
  }
}
