/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * ContactService (Angular)
 *
 * Handles syncing device + Google contacts with backend,
 * fetching matched contacts, and sending invites.
 *
 */

import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Contacts, GetContactsOptions} from '@capacitor-community/contacts';
import {AuthService} from '../auth/auth.service';

export interface ContactPayload {
  contactName: string;
  phoneNormalized: string;
  phoneRaw: string;
}

export interface MatchedContact {
  contactName: string;
  contactId: string;
  phoneNormalized: string;
  registered: boolean;
  email?: string;
}

@Injectable({providedIn: 'root'})
export class ContactService {
  private baseUrl = '/api/contacts';

  constructor(private http: HttpClient, private auth: AuthService) {
  }

  /** 📱 Sync device contacts (backend expects { ownerUserId, contacts[] }) */
  syncContacts(contacts: ContactPayload[]) {
    const ownerUserId = this.auth.getUserId();
    return this.http.post(`${this.baseUrl}/sync`, {
      ownerUserId,
      contacts,
    });
  }

  /** 🔑 Sync Google contacts (backend expects { accessToken }) */
  syncGoogleContacts(userId: number, googleAccessToken: string) {
    return this.http.post(`${this.baseUrl}/google/sync?userId=${userId}`, {
      accessToken: googleAccessToken,
    });
  }

  /** ✅ Get matched contacts from backend */
  getMatchedContacts(userId: number) {
    return this.http.get<MatchedContact[]>(`${this.baseUrl}/matched?userId=${userId}`);
  }

  /** 📱 Fetch contacts from device using Capacitor plugin */
  async getPhoneContacts(): Promise<ContactPayload[]> {
    try {
      const options: GetContactsOptions = {projection: 'all' as any};
      const result = await Contacts.getContacts(options);

      const contacts: ContactPayload[] = [];
      (result.contacts || []).forEach((c: any) => {
        if (c.phoneNumbers && c.phoneNumbers.length > 0) {
          contacts.push({
            contactName: c.name || '',
            phoneNormalized: c.phoneNumbers[0].number || '',
            phoneRaw: c.phoneNumbers[0].number || '',
          });
        }
      });

      return contacts;
    } catch (err) {
      console.error('Error fetching device contacts', err);
      return [];
    }
  }

  /** 📧 Send invite email */
  sendInviteEmail(contactEmail: string, contactName: string) {
    return this.http.post(`${this.baseUrl}/invite/send`, {
      contactEmail,
      contactName,
    });
  }
}
