/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/*
 * SyncContactsComponent
 *
 * Handles syncing phone + Google contacts after registration/login.
 */

import {Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {firstValueFrom} from 'rxjs';
import {Router} from '@angular/router';
import {ContactPayload, ContactService} from '../contact.service';
import {AuthService} from '../../auth/auth.service';
import {Contacts, GetContactsOptions} from '@capacitor-community/contacts';

// Google Identity
declare const google: any;

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
    this.firstName = this.authService.getFirstName();
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

      const contacts: ContactPayload[] = (result.contacts || []).map((c: any) => ({
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

  /** 🔑 Sync Google contacts */
  async syncGoogleContacts() {
    if (this.userId === null) return;
    this.syncing = true;

    try {
      const client = google.accounts.oauth2.initTokenClient({
        client_id: '804357637525-auufm7hjj51mtsugsgnlqkudptiln1ba.apps.googleusercontent.com', // 👈 your Google Client ID
        scope: 'https://www.googleapis.com/auth/contacts.readonly',
        callback: async (response: any) => {
          if (response && response.access_token) {
            try {
              await firstValueFrom(
                this.contactService.syncGoogleContacts(this.userId!, response.access_token)
              );
              this.syncing = false;
              this.router.navigate(['/chats']);
            } catch (err) {
              console.error('syncGoogleContacts backend error', err);
              this.errorMsg = 'Backend failed to sync Google contacts';
              this.syncing = false;
            }
          } else {
            this.errorMsg = 'No access token received from Google';
            this.syncing = false;
          }
        },
      });

      client.requestAccessToken();
    } catch (err) {
      console.error('syncGoogleContacts error', err);
      this.syncing = false;
      this.errorMsg = 'Google OAuth failed';
    }
  }

  /** Skip syncing */
  skipSync() {
    this.router.navigate(['/chats']);
  }
}
