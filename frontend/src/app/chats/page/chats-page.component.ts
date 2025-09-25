/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, NavigationEnd, Router, RouterOutlet} from '@angular/router';
import {CommonModule} from '@angular/common';
import {filter, firstValueFrom, of} from 'rxjs';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {AuthService} from '../../auth/auth.service';
import {AppModalComponent} from '../../shared/modal/app-modal.component';
import {catchError} from 'rxjs/operators';

declare const google: any;

@Component({
  selector: 'app-chats-page',
  standalone: true,
  imports: [CommonModule, RouterOutlet, AppModalComponent],
  templateUrl: './chats-page.component.html',
  styleUrls: ['./chats-page.component.css'],
})
export class ChatsPageComponent implements OnInit {
  userId: number | null = null;
  firstName: string | null = null;
  showGooglePopup = false;
  errorMsg = '';
  activeChat = false;
  success = false;
  syncing = false;
  showContactsList = true;

  currentContact: MatchedContact | null = null;

  constructor(
    public router: Router,
    private route: ActivatedRoute,
    private contactService: ContactService,
    private authService: AuthService
  ) {
  }

  async ngOnInit() {
    this.firstName = this.authService.getFirstName();
    const uid = this.authService.getUserId();
    if (!uid) {
      this.router.navigate(['/login']);
      return;
    }
    this.userId = Number(uid);

    this.route.queryParams.subscribe((params) => {
      if (params['showGooglePopup'] === 'true') {
        this.openGooglePopup();
      }
    });

    // Load contacts once at startup
    this.contactService.getMatchedContacts(true).subscribe({
      next: (list) => {
        if (!list || list.length === 0) {
          this.openGooglePopup();
        }
        // Store and notify listeners
        this.contactService.setCachedContacts(list);
        this.contactService.notifyContactsUpdated();
      },
      error: (err) => {
        console.error('Failed to load matched contacts on start', err);
        this.openGooglePopup();
      },
    });


    // 🔹 Track navigation (update current chat/contact)
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      const url = this.router.url;
      this.activeChat = url.startsWith('/chats/') && url !== '/chats';

      if (window.innerWidth < 768) {
        this.showContactsList = !this.activeChat;
      } else {
        this.showContactsList = true;
      }

      if (this.activeChat) {
        const contactId = url.split('/').pop();
        const cached = this.contactService.getCachedContacts() || [];
        this.currentContact = cached.find(c => String(c.contactId) === contactId) || null;
      } else {
        this.activeChat = false;
        this.currentContact = null;
      }
    });
  }

  goBack() {
    this.router.navigate(['/chats']);
    if (window.innerWidth < 768) {
      this.showContactsList = true;
    }
  }

  openGooglePopup() {
    this.showGooglePopup = true;
    this.errorMsg = '';
    this.success = false;
    this.syncing = false;
  }

  closePopup() {
    this.showGooglePopup = false;
    this.errorMsg = '';
    this.success = false;
    this.syncing = false;
  }

  async connectWithGoogle() {
    if (!this.userId) return;
    this.syncing = true;

    try {
      const client = google.accounts.oauth2.initTokenClient({
        client_id:
          '804357637525-auufm7hjj51mtsugsgnlqkudptiln1ba.apps.googleusercontent.com',
        scope:
          'https://www.googleapis.com/auth/contacts.readonly https://www.googleapis.com/auth/gmail.send',
        callback: async (response: any) => {
          if (response?.access_token) {
            try {
              await firstValueFrom(this.contactService.syncGoogleContacts(response.access_token));

              await firstValueFrom(
                this.contactService.getMatchedContacts(true).pipe(
                  catchError((err) => {
                    console.error('Failed to fetch matched after sync', err);
                    return of([]);
                  })
                )
              );

              this.contactService.notifyContactsUpdated();
              this.success = true;
            } catch (err) {
              console.error('Backend sync error', err);
              this.errorMsg = 'Backend failed to sync Google contacts';
            } finally {
              this.syncing = false;
              setTimeout(() => this.closePopup(), 1000);
            }
          } else {
            this.errorMsg = 'No access token received from Google';
            this.syncing = false;
            this.closePopup();
          }
        },
      });

      client.requestAccessToken();
    } catch (err) {
      console.error('Google OAuth failed', err);
      this.errorMsg = 'Google OAuth failed';
      this.syncing = false;
      this.closePopup();
    }
  }

  contactsEmpty(): boolean {
    const cached = this.contactService.getCachedContacts();
    return !this.router.url.includes('/chats/') && (!cached || cached.length === 0);
  }
}
