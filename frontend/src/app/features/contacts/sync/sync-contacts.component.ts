/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {firstValueFrom} from 'rxjs';
import {ContactService} from '../contact.service';
import {AuthService} from '../../auth/auth.service';

@Component({
  selector: 'app-sync-contacts',
  standalone: true,
  imports: [CommonModule],       // ✅ FIX WebStorm unresolved vars
  templateUrl: './sync-contacts.component.html',
  styleUrls: ['./sync-contacts.component.css']
})
export class SyncContactsComponent implements OnInit {

  userId: number | null = null;
  firstName: string | null = null;
  syncing = false;
  errorMsg = '';
  showPopupWarning = false;

  constructor(
    private contactService: ContactService,
    private authService: AuthService,
    private router: Router
  ) {
  }

  ngOnInit(): void {
    this.firstName = this.authService.getFirstName();
    this.userId = Number(this.authService.getUserId());
  }

  async syncDeviceContacts(): Promise<void> {
    this.syncing = true;
    try {
      await firstValueFrom(
        this.contactService.syncContacts(this.userId!, [])
      );
      this.redirectAfterSync();
    } catch {
      this.errorMsg = 'Failed to sync device contacts.';
      this.syncing = false;
    }
  }

  async syncGoogleContacts(): Promise<void> {
    this.syncing = true;
    try {
      await firstValueFrom(this.contactService.syncGoogleContacts());
      await firstValueFrom(this.contactService.syncGoogleContacts());
      this.redirectAfterSync();
    } catch {
      this.showPopupWarning = true;
      this.syncing = false;
    }
  }

  closeWarning() {
    this.showPopupWarning = false;
  }

  skipSync(): void {
    this.router.navigate(['/chats']);
  }

  private redirectAfterSync(): void {
    this.contactService.getMatchedContacts(true).subscribe(() => {
      this.syncing = false;
      this.router.navigate(['/chats']);
    });
  }
}
