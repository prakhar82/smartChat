/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {catchError, firstValueFrom, of} from 'rxjs';
import {Router} from '@angular/router';
import {ContactPayload, ContactService} from '../../contacts/contact.service';
import {AuthService} from '../../auth/auth.service';
import {Contacts, GetContactsOptions} from '@capacitor-community/contacts';

// Google Identity Services
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
    this.userId = Number(uid);
  }

  /** 📱 Sync phone/device contacts */
  async syncDeviceContacts() {
    if (this.userId === null) return;
    this.syncing = true;

    try {
      const options: GetContactsOptions = {
        projection: {
          name: true,
          phones: true,
        },
      };

      const result = await Contacts.getContacts(options);

      const contacts: ContactPayload[] =
        result.contacts.map((c) => ({
          contactName: c.name?.display ?? 'Unknown',
          phoneRaw: c.phones?.[0]?.number ?? '',
          phoneNormalized: (c.phones?.[0]?.number ?? '').replace(/\D/g, ''), // keep digits only
        })) ?? [];

      await firstValueFrom(
        this.contactService.syncContacts(this.userId!, contacts)
      );

      this.syncing = false;
      this.router.navigate(['/chats']);
    } catch (err) {
      console.error('syncDeviceContacts error', err);
      this.errorMsg = 'Failed to sync device contacts';
      this.syncing = false;
    }
  }

  /** 🔑 Sync Google contacts */
  async syncGoogleContacts() {
    if (this.userId === null) return;
    this.syncing = true;

    try {
      const client = google.accounts.oauth2.initTokenClient({
        client_id:
          '804357637525-auufm7hjj51mtsugsgnlqkudptiln1ba.apps.googleusercontent.com',
        scope: 'https://www.googleapis.com/auth/contacts.readonly',
        callback: async (response: any) => {
          if (response && response.access_token) {
            try {
              await firstValueFrom(
                this.contactService.syncGoogleContacts(response.access_token)
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


  /** Skip syncing → go to chats and open Google popup if no contacts */
  async skipSync() {
    this.router.navigate(['/chats'], {queryParams: {showGooglePopup: 'true'}}).then(async () => {
      try {
        const contacts = await firstValueFrom(
          this.contactService.getMatchedContacts(true).pipe(
            catchError((err) => {
              console.error('❌ Failed to load contacts after skip', err);
              return of([]);
            })
          )
        );

        // If contacts exist → notify components & remove popup flag
        if (contacts && contacts.length > 0) {
          this.contactService.notifyContactsUpdated();
          this.router.navigate([], {
            queryParams: {showGooglePopup: null},
            queryParamsHandling: 'merge',
          });
        }
      } catch (err) {
        console.error('❌ Silent reload after skip failed', err);
      }
    });
  }

}
