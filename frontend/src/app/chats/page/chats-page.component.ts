/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, NavigationEnd, Router, RouterOutlet} from '@angular/router';
import {CommonModule} from '@angular/common';
import {filter, firstValueFrom, of, Subscription} from 'rxjs';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {AuthService} from '../../auth/auth.service';
import {AppModalComponent} from '../../shared/modal/app-modal.component';
import {catchError} from 'rxjs/operators';
import {ChatService} from '../chat.service';

declare const google: any;

@Component({
  selector: 'app-chats-page',
  standalone: true,
  imports: [CommonModule, RouterOutlet, AppModalComponent],
  templateUrl: './chats-page.component.html',
  styleUrls: ['./chats-page.component.css'],
})
export class ChatsPageComponent implements OnInit, OnDestroy {
  userId: number | null = null;
  firstName: string | null = null;
  showGooglePopup = false;
  errorMsg = '';
  activeChat = false;
  success = false;
  syncing = false;
  showContactsList = true;
  isConnected = false;

  currentContact: MatchedContact | null = null;
  private connectionSub?: Subscription;
  private reconnectTimer?: any;

  constructor(
    public router: Router,
    private route: ActivatedRoute,
    private contactService: ContactService,
    private authService: AuthService,
    private chatService: ChatService
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

    // initial connect
    this.chatService.connectWebSocket(this.userId);

    // ✅ monitor connection state
    this.connectionSub = this.chatService.connection$.subscribe((state: boolean) => {
      this.isConnected = state;
      console.log('[ChatsPageComponent] WebSocket connected?', state);

      if (!state && this.userId) {
        // schedule reconnect in 5s if not already scheduled
        if (!this.reconnectTimer) {
          console.warn('[ChatsPageComponent] ⚠️ Lost connection, scheduling reconnect in 5s');
          this.reconnectTimer = setTimeout(() => {
            this.reconnectTimer = null;
            this.reconnect();
          }, 5000);
        }
      }
    });

    this.route.queryParams.subscribe((params) => {
      if (params['showGooglePopup'] === 'true') {
        this.openGooglePopup();
      }
    });

    this.contactService.getMatchedContacts(true).subscribe({
      next: (list) => {
        if (!list || list.length === 0) {
          this.openGooglePopup();
        }
        this.contactService.setCachedContacts(list);
        this.contactService.notifyContactsUpdated();
      },
      error: (err) => {
        console.error('[ChatsPageComponent] ❌ Failed to load matched contacts', err);
        this.openGooglePopup();
      },
    });

    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      const url = this.router.url;
      this.activeChat = url.startsWith('/chats/') && url !== '/chats';

      if (window.innerWidth < 768) {
        this.showContactsList = !this.activeChat;
      } else {
        this.showContactsList = true;
      }

      if (this.activeChat) {
        const matchedUserId = url.split('/').pop();
        const cached = this.contactService.getCachedContacts() || [];
        this.currentContact =
          cached.find((c) => String(c.matchedUserId) === matchedUserId) || null;
      } else {
        this.activeChat = false;
        this.currentContact = null;
      }
    });
  }

  ngOnDestroy(): void {
    this.connectionSub?.unsubscribe();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }
    this.chatService.disconnectWebSocket();
  }

  reconnect() {
    if (this.userId) {
      console.log('[ChatsPageComponent] 🔄 Attempting reconnect…');
      this.chatService.disconnectWebSocket();
      this.chatService.connectWebSocket(this.userId);
    }
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

  contactsEmpty(): boolean {
    const cached = this.contactService.getCachedContacts();
    return !this.router.url.includes('/chats/') && (!cached || cached.length === 0);
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
                    console.error('[ChatsPageComponent] ❌ Failed to fetch matched after sync', err);
                    return of([]);
                  })
                )
              );

              this.contactService.notifyContactsUpdated();
              this.success = true;
            } catch (err) {
              console.error('[ChatsPageComponent] ❌ Backend sync error', err);
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
      console.error('[ChatsPageComponent] ❌ Google OAuth failed', err);
      this.errorMsg = 'Google OAuth failed';
      this.syncing = false;
      this.closePopup();
    }
  }
}
