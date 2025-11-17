/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, HostListener, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {animate, style, transition, trigger} from '@angular/animations';
import {Subscription} from 'rxjs';

import {UserService} from '../../auth/user.service';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {ChatService} from '../chat.service';
import {PresenceService} from '../../shared/presence/presence.service';
import {LeftSidebarComponent} from '../sidebar/left-sidebar.component';
import {ContactListComponent} from '../list/contact-list.component';
import {ChatWindowComponent} from '../window/chat-window.component';
import {TopHeaderComponent} from '../top-header/top-header.component';
import {ThemeService} from '../../shared/theme/theme.service';

@Component({
  selector: 'app-chats-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LeftSidebarComponent,
    ContactListComponent,
    ChatWindowComponent,
    TopHeaderComponent,
  ],
  templateUrl: './chats-page.component.html',
  styleUrls: ['./chats-page.component.scss'],
  animations: [
    trigger('fadeSlideIn', [
      transition(':enter', [
        style({opacity: 0, transform: 'translateY(10px)'}),
        animate('300ms ease-out', style({opacity: 1, transform: 'translateY(0)'})),
      ]),
      transition(':leave', [
        animate('200ms ease-in', style({opacity: 0, transform: 'translateY(10px)'})),
      ]),
    ]),
  ],
})
export class ChatsPageComponent implements OnInit, OnDestroy {
  /** 👤 User Info */
  firstName = '';
  userId?: number;
  userStatus: 'available' | 'away' | 'busy' = 'available';

  /**
   * connectionState:
   * - 'online' => chat connected
   * - 'offline' => chat disconnected
   * - 'reconnecting' => chat connecting / presence reconnecting
   */
  connectionState: 'online' | 'offline' | 'reconnecting' = 'offline';

  /** boolean to pass to ChatWindowComponent */
  isConnected = false;

  private lastActivity = new Date();

  /** 🌓 Theme */
  isDarkTheme = false;

  /** 📇 Contacts & Search */
  contacts: MatchedContact[] = [];
  filteredContacts: MatchedContact[] = [];
  searchQuery = '';

  /** 💬 UI States */
  selectedContactId?: number;
  selectedContactInfo?: MatchedContact;
  isChatOpen = false;
  isMobileView = false;

  /** 🪟 Split Pane */
  contactPanelWidth = 350;
  private resizing = false;
  private resizeDebounce?: any;

  /** 🧹 Subscriptions */
  private subs = new Subscription();
  private idleInterval?: ReturnType<typeof setInterval>;

  constructor(
    private readonly userService: UserService,
    private readonly contactService: ContactService,
    private readonly chatService: ChatService,
    private readonly presenceService: PresenceService,
    private readonly themeService: ThemeService
  ) {
  }

  /* =========================================================
   * 🚀 Lifecycle
   * ========================================================= */
  ngOnInit(): void {
    this.loadUserAndConnect();
    this.loadContacts();
    this.startIdleWatcher();
    this.restorePanelWidth();
    this.checkViewport();

    // 🎨 Theme watcher
    this.subs.add(
      this.themeService.theme$.subscribe((theme) => {
        this.isDarkTheme = theme === 'dark';
      })
    );

    // 🔌 Monitor STOMP connection (ChatService.getConnectionState())
    this.subs.add(
      this.chatService.getConnectionState().subscribe((state: 'connecting' | 'connected' | 'disconnected') => {
        switch (state) {
          case 'connected':
            this.connectionState = 'online';
            this.isConnected = true;
            break;
          case 'connecting':
            this.connectionState = 'reconnecting';
            this.isConnected = false;
            break;
          case 'disconnected':
          default:
            this.connectionState = 'offline';
            this.isConnected = false;
            break;
        }
      })
    );

    // 🧩 Monitor presence reconnects (presence overrides)
    this.subs.add(
      this.presenceService.connection$.subscribe((connected) => {
        if (connected) {
          // presence says we're connected — treat UI as online
          this.connectionState = 'online';
          this.isConnected = true;
        } else {
          // if chat already reported connected, keep it; otherwise show reconnecting
          if (this.connectionState === 'online') {
            this.connectionState = 'reconnecting';
            this.isConnected = false;
          }
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    if (this.idleInterval) clearInterval(this.idleInterval);
    if (this.resizeDebounce) clearTimeout(this.resizeDebounce);
  }

  /* =========================================================
   * 👤 User & Connection
   * ========================================================= */
  private loadUserAndConnect(): void {
    const sub = this.userService.getUserProfile().subscribe({
      next: async (user) => {
        this.firstName = user?.firstName || 'User';
        this.userId = user?.id ?? user?.userId ?? undefined;

        if (this.userId) {
          // connect chat + presence services
          try {
            await this.chatService.connectStomp(this.userId);
          } catch (err) {
            console.warn('[ChatsPage] ❌ chatService.connectStomp error:', err);
          }
          try {
            await this.presenceService.connect(String(this.userId));
          } catch (err) {
            console.warn('[ChatsPage] ❌ presenceService.connect error:', err);
          }
        }
      },
      error: (err) => console.error('[ChatsPage] ❌ Failed to load user profile:', err),
    });
    this.subs.add(sub);
  }

  /* =========================================================
   * 📇 Contacts
   * ========================================================= */
  private loadContacts(): void {
    const sub = this.contactService.getMatchedContacts().subscribe({
      next: (list) => {
        const withPresence = this.presenceService.mergeWithContacts(list);
        this.contacts = withPresence;
        this.filteredContacts = withPresence;
      },
      error: (err) => console.error('[ChatsPage] ❌ Failed to fetch contacts:', err),
    });
    this.subs.add(sub);
  }

  filterContacts(): void {
    const term = this.searchQuery.toLowerCase().trim();
    this.filteredContacts = !term
      ? this.contacts
      : this.contacts.filter((c) => {
        const name = c.contactName?.toLowerCase() || '';
        const phone = c.phones?.map((p) => p.value?.toLowerCase()).join(' ') || '';
        return name.includes(term) || phone.includes(term);
      });
  }

  /* =========================================================
   * 💬 Chat Actions
   * ========================================================= */
  openChat(contact: MatchedContact): void {
    const id = Number(contact?.matchedUserId || contact?.contactId);
    if (!id || isNaN(id)) {
      console.warn('[ChatsPage] ⚠️ Invalid contact ID', contact);
      return;
    }

    this.selectedContactId = id;
    this.selectedContactInfo = contact;
    this.isChatOpen = true;
  }

  closeChat(): void {
    this.selectedContactId = undefined;
    this.selectedContactInfo = undefined;
    this.isChatOpen = false;
  }

  /* =========================================================
   * 🕒 Idle Handling
   * ========================================================= */
  private startIdleWatcher(): void {
    this.idleInterval = setInterval(() => {
      const idle = (Date.now() - this.lastActivity.getTime()) / 1000;
      if (idle > 60 && this.userStatus !== 'away') this.userStatus = 'away';
    }, 10000);
  }

  @HostListener('window:mousemove')
  @HostListener('window:keydown')
  resetTimer(): void {
    if (this.userStatus !== 'available') this.userStatus = 'available';
    this.lastActivity = new Date();
  }

  /* =========================================================
   * 🪟 Resizer
   * ========================================================= */
  startResize(event: MouseEvent | TouchEvent): void {
    if (this.isMobileView) return;
    this.resizing = true;
    document.body.style.userSelect = 'none';

    const moveListener = (e: MouseEvent | TouchEvent) => {
      const clientX = e instanceof MouseEvent ? e.clientX : e.touches[0].clientX;
      const newWidth = Math.max(240, Math.min(500, clientX - 70));
      this.contactPanelWidth = newWidth;
    };

    const stopListener = () => {
      this.resizing = false;
      document.body.style.userSelect = '';
      clearTimeout(this.resizeDebounce);
      this.resizeDebounce = setTimeout(() => {
        localStorage.setItem('chat_contact_panel_width', String(this.contactPanelWidth));
      }, 500);

      window.removeEventListener('mousemove', moveListener);
      window.removeEventListener('touchmove', moveListener);
      window.removeEventListener('mouseup', stopListener);
      window.removeEventListener('touchend', stopListener);
    };

    window.addEventListener('mousemove', moveListener);
    window.addEventListener('touchmove', moveListener);
    window.addEventListener('mouseup', stopListener);
    window.addEventListener('touchend', stopListener);
  }

  private restorePanelWidth(): void {
    const saved = localStorage.getItem('chat_contact_panel_width');
    if (saved) {
      const width = +saved;
      if (width >= 240 && width <= 500) this.contactPanelWidth = width;
    }
  }

  /* =========================================================
   * 📱 Responsive
   * ========================================================= */
  @HostListener('window:resize')
  checkViewport(): void {
    this.isMobileView = window.innerWidth <= 768;
  }

  scrollToTop(): void {
    if (typeof window !== 'undefined') {
      window.scrollTo({top: 0, behavior: 'smooth'});
    }
  }

  /* =========================================================
   * 🎨 Theme
   * ========================================================= */
  toggleTheme(): void {
    this.themeService.toggleTheme();
  }
}
