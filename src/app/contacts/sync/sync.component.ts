/*
 * ContactSyncComponent
 * - Uses Capacitor Contacts and Google Sign-In (via plugin) on mobile
 * - Uses ContactService to send numbers to backend
 */

import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ContactService } from '../contact.service';

// Capacitor plugin imports (only used in mobile/Capacitor runtime)
import { Plugins } from '@capacitor/core';
const { Contacts } = Plugins;

@Component({
  selector: 'app-contact-sync',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sync.component.html',
  styleUrls: ['./sync.component.css']
})
export class ContactSyncComponent {
  userId = Number(localStorage.getItem('userId')) || null; // TODO: replace with real logged-in user id
  synced = false;

  constructor(private contactService: ContactService) {}

  async syncPhoneContacts() {
    if (this.userId === null) {
      alert('User ID not found');
      return;
    }
    try {
      const result: any = await (Contacts as any).getContacts();
      const phoneNumbers: string[] = [];

      (result?.contacts || []).forEach((c: any) => {
        (c.phoneNumbers || []).forEach((p: any) => {
          if (p && p.number) phoneNumbers.push(p.number);
        });
      });

      await this.contactService.syncPhoneContacts(this.userId, phoneNumbers).toPromise();
      this.synced = true;
      alert('📱 Phone contacts synced');
    } catch (err) {
      console.error('syncPhoneContacts error', err);
      alert('Failed to sync phone contacts');
    }
  }

  async syncGoogleContacts() {
    if (this.userId === null) {
      alert('User ID not found');
      return;
    }
    try {
      const googleAccessToken = prompt('Paste Google access token (for testing)');
      if (!googleAccessToken) return;

      await this.contactService.syncGoogleContacts(this.userId, googleAccessToken).toPromise();
      this.synced = true;
      alert('🔑 Google contacts synced');
    } catch (err) {
      console.error('syncGoogleContacts error', err);
      alert('Failed to sync Google contacts');
    }
  }
}
