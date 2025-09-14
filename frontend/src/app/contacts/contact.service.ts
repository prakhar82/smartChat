import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Contacts, GetContactsOptions } from '@capacitor-community/contacts';
import { AuthService } from '../auth/auth.service';

export interface MatchedContact {
  contactName: string;
  phoneNormalized: string;
  phoneRaw: string;
}

@Injectable({ providedIn: 'root' })
export class ContactService {
  private baseUrl = '/api/contacts';

  constructor(private http: HttpClient, private auth: AuthService) {}

  /** 📱 Sync device contacts */
  syncContacts(contacts: { contactName: string; phoneNormalized: string; phoneRaw: string }[]) {
    const ownerUserId = this.auth.getUserId();
    return this.http.post(`${this.baseUrl}/sync`, {
      ownerUserId,
      contacts,
    });
  }

  /** ✅ Wrapper: sync raw phone numbers */
  syncPhoneContacts(userId: number, phoneNumbers: string[]) {
    const contacts = phoneNumbers.map((num) => ({
      contactName: '',
      phoneNormalized: num,
      phoneRaw: num,
    }));
    return this.http.post(`${this.baseUrl}/sync`, { ownerUserId: userId, contacts });
  }

  /** Get matched contacts from backend */
  getMatchedContacts(userId: number) {
    return this.http.get<MatchedContact[]>(`${this.baseUrl}/matched?userId=${userId}`);
  }

  /** 🔑 Sync Google contacts (send SmartChat JWT in headers, Google token in body) */
  syncGoogleContacts(userId: number, googleAccessToken: string) {
    return this.http.post(`/api/contacts/google/sync?userId=${userId}`, {
      accessToken: googleAccessToken,
    });
  }

  /** 📱 Fetch phone contacts from device */
  async getPhoneContacts(): Promise<string[]> {
    try {
      const options: GetContactsOptions = { projection: 'all' as any };
      const result = await Contacts.getContacts(options);

      const phoneNumbers: string[] = [];
      (result.contacts || []).forEach((c: any) => {
        (c.phoneNumbers || []).forEach((p: any) => {
          if (p?.number) phoneNumbers.push(p.number);
        });
      });

      return phoneNumbers;
    } catch (err) {
      console.error('Error fetching device contacts', err);
      return [];
    }
  }

  /** 📧 Send invite email */
  sendInviteEmail(contactEmail: string, contactName: string) {
    return this.http.post(`${this.baseUrl}/invite/send`, { contactEmail, contactName });
  }
}
