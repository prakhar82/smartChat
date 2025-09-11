/*
 * ContactService (Angular)
 *
 * Purpose:
 *  - Provide methods to sync phone contacts and Google contacts with backend
 *  - Keep API calls centralized for re-use by components
 */

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class ContactService {
  private baseUrl = '/api';

  constructor(private http: HttpClient) {}

  syncPhoneContacts(userId: number, phoneNumbers: string[]) {
    return this.http.post(`${this.baseUrl}/contacts/sync`, { userId, contacts: phoneNumbers });
  }

  // Google contacts sync: front-end obtains Google token and passes via Authorization header
  syncGoogleContacts(userId: number, googleAccessToken: string) {
    return this.http.post(
      `${this.baseUrl}/contacts/google/sync?userId=${userId}`,
      {},
      { headers: { Authorization: `Bearer ${googleAccessToken}` } }
    );
  }

  getMatchedContacts(userId: number) {
    return this.http.get<any[]>(`${this.baseUrl}/contacts/matched?userId=${userId}`);
  }
}
