/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {firstValueFrom} from 'rxjs';
import {Router} from '@angular/router';
import {ContactService} from '../contact.service';
import {AuthService} from '../../auth/auth.service';

// Capacitor Contacts plugin
import {Contacts, GetContactsOptions} from '@capacitor-community/contacts';

interface MyContact {
  id?: string;
  name?: string;
  phoneNumbers?: { number?: string }[];
}

@Component({
  selector: 'app-sync-contacts',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sync.component.html',
  styleUrls: ['./sync.component.css'],
})
export class SyncContactsComponent implements OnInit {
  userId: number | null = null;
  firstName: string | null = null;
  syncing = false;
  errorMsg = '';

  constructor(
    private contactService: ContactService,
    private authService: AuthService,
    private router: Router
  ) {
  }

  ngOnInit() {
    let firstName = this.authService.getFirstName();
    const uid = this.authService.getUserId();
    if (!uid) {
      this.errorMsg = 'User not logged in';
      return;
    }
    this.userId = uid;
  }

  /** 📱 Sync phone/device contacts */
  async syncPhoneContacts() {
    if (this.userId === null) return;
    this.syncing = true;

    try {
      const options: GetContactsOptions = {projection: 'all' as any};
      const result = await Contacts.getContacts(options);

      const contacts = (result.contacts as MyContact[] || []).map((c) => ({
        contactName: c.name || '',
        phoneNormalized: c.phoneNumbers?.[0]?.number || '',
        phoneRaw: c.phoneNumbers?.[0]?.number || '',
      }));

      await firstValueFrom(this.contactService.syncContacts(contacts));

      this.syncing = false;
      this.router.navigate(['/chats']); // ✅ redirect after sync
    } catch (err) {
      console.error('syncPhoneContacts error', err);
      this.syncing = false;
      this.errorMsg = 'Failed to sync phone contacts';
    }
  }

  /** 🔑 Sync Google contacts via OAuth2 access token */
  async syncGoogleContacts() {
    if (this.userId === null) return;
    this.syncing = true;

    try {
      const googleAccessToken = prompt('Paste Google access token (for testing)');
      if (!googleAccessToken) return;

      await firstValueFrom(
        this.contactService.syncGoogleContacts(this.userId, googleAccessToken)
      );

      this.syncing = false;
      this.router.navigate(['/chats']); // ✅ redirect after sync
    } catch (err) {
      console.error('syncGoogleContacts error', err);
      this.syncing = false;
      this.errorMsg = 'Failed to sync Google contacts';
    }
  }

  /** Skip syncing and go directly to chats */
  skipSync() {
    this.router.navigate(['/chats']);
  }

}
