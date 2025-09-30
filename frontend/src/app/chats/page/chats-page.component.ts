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
import {Subscription} from 'rxjs';
import {filter} from 'rxjs/operators';
import {AuthService} from '../../auth/auth.service';
import {ChatService} from '../chat.service';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {AppModalComponent} from '../../shared/modal/app-modal.component';
import {ContactListComponent} from '../list/contact-list.component';

@Component({
  selector: 'app-chats-page',
  standalone: true,
  imports: [CommonModule, RouterOutlet, AppModalComponent, ContactListComponent],
  templateUrl: './chats-page.component.html',
  styleUrls: ['./chats-page.component.css'],
})
export class ChatsPageComponent implements OnInit, OnDestroy {
  userId: number | null = null;
  firstName: string | null = null;
  isConnected = false;

  activeChatId: string | null = null;
  currentContact: MatchedContact | null = null;
  showContactsList = true;

  // popup state
  showGooglePopup = false;
  errorMsg = '';
  syncing = false;
  success = false;

  // contacts state
  allContacts: MatchedContact[] = [];
  filteredContacts: MatchedContact[] = [];

  private connectionSub?: Subscription;
  private routerSub?: Subscription;
  private reconnectTimer?: any;
  protected isMobile = window.innerWidth < 768;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService,
    private chatService: ChatService,
    protected contactService: ContactService
  ) {
  }

  ngOnInit(): void {
    // ✅ SmartChat JWT
    const jwt = this.authService.getToken();
    if (!jwt) {
      this.router.navigate(['/login']);
      return;
    }

    // ✅ Handle google_token from callback
    this.route.queryParams.subscribe(params => {
      const token = params['google_token'] || params['access_token'];
      if (token) {
        localStorage.setItem('google_token', token);
        console.log("✅ Google token saved");

        this.contactService.syncGoogleContacts(token).subscribe({
          next: () => {
            console.log("✅ Google contacts sync triggered");
            this.refreshContacts();
          },
          error: (err) => console.error("❌ Google contacts sync failed", err)
        });

        this.closePopup();
      }
    });

    // ✅ Always reload contacts after login
    this.refreshContacts();

    this.firstName = this.authService.getFirstName();
    const uid = this.authService.getUserId();
    if (!uid) {
      this.router.navigate(['/login']);
      return;
    }
    this.userId = Number(uid);

    // 🔌 Connect websocket
    this.chatService.connectWebSocket(this.userId);

    // ✅ monitor WebSocket connection
    this.connectionSub = this.chatService.connection$.subscribe((state) => {
      this.isConnected = state;
      if (!state && this.userId && !this.reconnectTimer) {
        this.reconnectTimer = setTimeout(() => {
          this.reconnectTimer = null;
          this.reconnect();
        }, 5000);
      }
    });

    // ✅ handle route changes
    this.routerSub = this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => {
        const tree = this.router.parseUrl(this.router.url);
        const chatSegment = tree.root.children['chat']?.segments[0];
        this.activeChatId = chatSegment ? chatSegment.path : null;

        if (this.activeChatId) {
          this.currentContact =
            this.allContacts.find((c) => String(c.matchedUserId) === this.activeChatId) || null;
        } else {
          this.currentContact = null;
        }

        if (this.isMobile) {
          this.showContactsList = !this.activeChatId;
        } else {
          this.showContactsList = true;
        }
      });
  }

  ngOnDestroy(): void {
    this.connectionSub?.unsubscribe();
    this.routerSub?.unsubscribe();
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.chatService.disconnectWebSocket();
  }

  reconnect(): void {
    if (this.userId) {
      this.chatService.disconnectWebSocket();
      this.chatService.connectWebSocket(this.userId);
    }
  }

  // When user taps a contact → show chat panel full screen
  openChat(contactId: number): void {
    this.router.navigate([{outlets: {chat: [contactId]}}]);
    if (this.isMobile) {
      this.showContactsList = false; // hide list
    }
  }

  // Back button → return to list
  goBack(): void {
    this.router.navigate([{outlets: {primary: ['chats'], chat: null}}]);
    if (this.isMobile) {
      this.showContactsList = true; // show list again
    }
  }

  // 🔹 popup handlers
  openGooglePopup(): void {
    this.showGooglePopup = true;
    this.errorMsg = '';
    this.syncing = false;
    this.success = false;
  }

  closePopup(): void {
    this.showGooglePopup = false;
    this.errorMsg = '';
    this.syncing = false;
    this.success = false;
  }

  connectWithGoogle(): void {
    this.syncing = true;
    this.errorMsg = '';
    this.authService.connectWithGoogle();
  }

  // 🔹 Search contacts
  onSearch(term: string): void {
    const lower = term.toLowerCase();
    this.filteredContacts = this.allContacts.filter(
      (c) =>
        (c.contactName || '').toLowerCase().includes(lower) ||
        c.emails.some((e) => e.value.toLowerCase().includes(lower)) ||
        c.phones.some((p) => p.value.includes(lower))
    );
  }

  // 🔹 Refresh contacts with sorting
  private refreshContacts(): void {
    this.contactService.getMatchedContacts(true).subscribe({
      next: (list) => {
        if (list && list.length > 0) {
          this.allContacts = this.sortContacts(list);
          this.filteredContacts = this.allContacts;
          console.log('[ChatsPage] ✅ Loaded contacts automatically after login');
        } else {
          console.warn('[ChatsPage] ⚠️ No contacts found → opening sync popup');
          this.openGooglePopup();
        }
        this.contactService.setCachedContacts(this.allContacts);
        this.contactService.notifyContactsUpdated();
      },
      error: (err) => {
        console.error('[ChatsPage] ❌ Failed to load contacts', err);
        this.openGooglePopup();
      },
    });
  }

  // 🔹 Sort contacts: registered users first (alphabetical), then invites
  private sortContacts(list: MatchedContact[]): MatchedContact[] {
    return [...list].sort((a, b) => {
      if (a.registered && !b.registered) return -1;
      if (!a.registered && b.registered) return 1;
      return (a.contactName || '').localeCompare(b.contactName || '');
    });
  }
}
