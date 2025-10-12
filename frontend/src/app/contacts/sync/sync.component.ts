/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {firstValueFrom, switchMap, timer} from 'rxjs';
import {Router} from '@angular/router';
import {ContactPayload, ContactService} from '../contact.service';
import {AuthService} from '../../auth/auth.service';
import {Contacts, GetContactsOptions} from '@capacitor-community/contacts';

// Google Identity Services
declare const google: any;

/**
 * SyncContactsComponent
 * ---------------------
 * Handles syncing of device and Google contacts post-registration.
 */
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

  // ---------------------------------------------------------
  // 🧭 Lifecycle
  // ---------------------------------------------------------
  ngOnInit(): void {
    console.log('[SyncContactsComponent] 🚀 ngOnInit');

    this.firstName = this.authService.getFirstName();
    const uid = this.authService.getUserId();

    if (!uid) {
      console.warn('[SyncContactsComponent] ⚠️ No logged-in user found.');
      this.errorMsg = 'User not logged in';
      return;
    }

    this.userId = Number(uid);
    console.log('[SyncContactsComponent] ✅ Logged userId:', this.userId);
  }

  // ---------------------------------------------------------
  // 📱 Sync device contacts
  // ---------------------------------------------------------
  async syncDeviceContacts(): Promise<void> {
    if (this.userId === null) {
      console.warn('[SyncContactsComponent] ⚠️ syncDeviceContacts called without userId');
      return;
    }

    console.log('[SyncContactsComponent] 🔄 Starting device contacts sync...');
    this.syncing = true;

    try {
      const options: GetContactsOptions = {
        projection: {name: true, phones: true, emails: true},
      };

      const result = await Contacts.getContacts(options);
      console.log('[SyncContactsComponent] 📇 Retrieved contacts:', result.contacts.length);

      const contacts: ContactPayload[] =
        result.contacts.map((c) => {
          const phones =
            c.phones?.map((p) => ({
              label: 'mobile',
              value: (p.number ?? '').replace(/\D/g, ''), // keep digits only
            })) ?? [];

          const emails =
            c.emails?.map((e) => ({
              label: 'home',
              value: e.address ?? '',
            })) ?? [];

          return {
            contactName: c.name?.display ?? 'Unknown',
            phones,
            emails,
          };
        }) ?? [];

      await firstValueFrom(this.contactService.syncContacts(this.userId!, contacts));
      console.log('[SyncContactsComponent] ✅ Device contacts synced successfully');
      this.loadContactsAndRedirect();
    } catch (err) {
      console.error('[SyncContactsComponent] ❌ syncDeviceContacts error:', err);
      this.errorMsg = 'Failed to sync device contacts';
      this.syncing = false;
    }
  }

  // ---------------------------------------------------------
  // 🔑 Sync Google Contacts
  // ---------------------------------------------------------
  async syncGoogleContacts(): Promise<void> {
    console.log('[SyncContactsComponent] 🔄 Starting backend Google OAuth flow...');
    this.syncing = true;

    try {
      const initUrl = this.contactService.getGoogleAuthInitUrl();
      window.open(initUrl, '_blank', 'width=600,height=700');
      console.log('[SyncContactsComponent] 🌐 Opened Google OAuth popup via backend');

      // Wait 2 seconds and trigger sync automatically after redirect completes
      await firstValueFrom(
        timer(2500).pipe(
          switchMap(() => this.contactService.syncGoogleContacts())
        )
      );
    } catch (err) {
      console.error('[SyncContactsComponent] ❌ syncGoogleContacts error:', err);
      this.errorMsg = 'Google OAuth failed';
      this.syncing = false;
    }
  }

  // ---------------------------------------------------------
  // ⏭️ Skip sync and go to chats
  // ---------------------------------------------------------
  skipSync(): void {
    console.log('[SyncContactsComponent] ⏭️ Skipping sync → navigating to chats');
    this.router.navigate(['/chats'], {queryParams: {showGooglePopup: 'true'}});
  }

  // ---------------------------------------------------------
  // 🔄 Helper: Refresh contacts and redirect
  // ---------------------------------------------------------
  private loadContactsAndRedirect(): void {
    console.log('[SyncContactsComponent] 🔁 Reloading matched contacts...');
    this.contactService.getMatchedContacts(true).subscribe({
      next: (contacts) => {
        console.log(`[SyncContactsComponent] ✅ Loaded ${contacts.length} matched contacts`);
        this.contactService.setCachedContacts(contacts);
        this.contactService.notifyContactsUpdated();
        this.syncing = false;
        this.router.navigate(['/chats']);
      },
      error: (err) => {
        console.error('[SyncContactsComponent] ❌ Failed to refresh contacts:', err);
        this.syncing = false;
        this.router.navigate(['/chats']);
      },
    });
  }
}
