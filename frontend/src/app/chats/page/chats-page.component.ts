/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * 💬 ChatsPageComponent
 * ---------------------------------------------------------
 * The main SmartChat workspace container.
 * Displays:
 *  - Left sidebar
 *  - Contact list
 *  - Chat window
 * Handles:
 *  - Reactive user presence & theme
 *  - Search & filtering of contacts
 *  - Responsive layout (desktop/mobile)
 *  - Smooth fade/slide transitions
 * ---------------------------------------------------------
 */

import {Component, HostListener, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {animate, style, transition, trigger,} from '@angular/animations';

import {UserService} from '../../auth/user.service';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {LeftSidebarComponent} from '../sidebar/left-sidebar.component';
import {ContactListComponent} from '../list/contact-list.component';
import {ChatWindowComponent} from '../window/chat-window.component';
import {TopHeaderComponent} from '../top-header/top-header.component';

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
    // 🌟 Smooth fade + slide animation for chat and welcome panels
    trigger('fadeSlideIn', [
      transition(':enter', [
        style({opacity: 0, transform: 'translateY(10px)'}),
        animate('400ms ease-out', style({opacity: 1, transform: 'translateY(0)'})),
      ]),
      transition(':leave', [
        animate('300ms ease-in', style({opacity: 0, transform: 'translateY(10px)'})),
      ]),
    ]),
  ],
})
export class ChatsPageComponent implements OnInit {
  /** 👤 User Info */
  firstName = '';
  isConnected = true;
  isDarkTheme = false;
  userStatus: 'available' | 'away' = 'available';
  private lastActivity = new Date();

  /** 📇 Contacts & Search */
  contacts: MatchedContact[] = [];
  filteredContacts: MatchedContact[] = [];
  searchQuery = '';

  /** 💬 UI States */
  selectedContactId?: number;
  isMobileView = false;

  constructor(
    private readonly userService: UserService,
    private readonly contactService: ContactService
  ) {
  }

  /* =========================================================
   * 🚀 Lifecycle
   * ========================================================= */
  ngOnInit(): void {
    console.log('[ChatsPage] 🚀 Initialized');
    this.loadUserProfile();
    this.loadContacts();
    this.startIdleWatcher();
  }

  /* =========================================================
   * 👤 Load User
   * ========================================================= */
  private loadUserProfile(): void {
    this.userService.getUserProfile().subscribe({
      next: (user) => {
        this.firstName = user?.firstName || 'User';
        console.log('[ChatsPage] 👤 User profile loaded:', this.firstName);
      },
      error: (err) => console.error('[ChatsPage] ❌ Failed to load profile:', err),
    });
  }

  /* =========================================================
   * 📇 Contacts
   * ========================================================= */
  private loadContacts(): void {
    console.log('[ChatsPage] 📥 Loading contacts...');
    this.contactService.getMatchedContacts().subscribe({
      next: (list) => {
        this.contacts = list;
        this.filteredContacts = list;
        console.log(`[ChatsPage] ✅ Loaded ${list.length} contacts`);
      },
      error: (err) => console.error('[ChatsPage] ❌ Failed to fetch contacts:', err),
    });
  }

  /** 🔍 Live Search Filter */
  filterContacts(): void {
    const term = this.searchQuery.toLowerCase().trim();
    if (!term) {
      this.filteredContacts = this.contacts;
      return;
    }

    this.filteredContacts = this.contacts.filter((c) => {
      const name = c.contactName?.toLowerCase() || '';
      const phone =
        c.phones?.map((p) => p.value?.toLowerCase()).join(' ') || '';
      return name.includes(term) || phone.includes(term);
    });
  }

  /* =========================================================
   * 🕒 Idle / Presence Handling
   * ========================================================= */
  private startIdleWatcher(): void {
    setInterval(() => {
      const idle = (Date.now() - this.lastActivity.getTime()) / 1000;
      if (idle > 60 && this.userStatus !== 'away') {
        this.userStatus = 'away';
        console.log('[ChatsPage] 💤 User is now away');
      }
    }, 10000);
  }

  @HostListener('window:mousemove')
  @HostListener('window:keydown')
  resetTimer(): void {
    if (this.userStatus !== 'available')
      console.log('[ChatsPage] ✅ User active again');
    this.userStatus = 'available';
    this.lastActivity = new Date();
  }

  /* =========================================================
   * 🎨 Theme + Connection
   * ========================================================= */
  toggleTheme(): void {
    this.isDarkTheme = !this.isDarkTheme;
    console.log(
      '[ChatsPage] 🎨 Theme toggled →',
      this.isDarkTheme ? 'Dark' : 'Light'
    );
  }

  reconnect(): void {
    this.isConnected = true;
    console.log('[ChatsPage] 🔌 Reconnected');
  }

  /* =========================================================
   * 💬 Chat Actions
   * ========================================================= */
  openChat(contact: MatchedContact): void {
    this.selectedContactId =
      contact?.matchedUserId || Number(contact?.contactId);
    console.log('[ChatsPage] 💬 Opened chat with:', contact.contactName);
  }

  closeChat(): void {
    console.log('[ChatsPage] ⬅️ Closed chat window');
    this.selectedContactId = undefined;
  }

  /* =========================================================
   * 🔍 Search
   * ========================================================= */
  onSearch(query: string): void {
    const q = query.trim().toLowerCase();
    if (!q) {
      this.filteredContacts = this.contactService.getCachedContacts();
      return;
    }

    this.filteredContacts = this.contactService
      .getCachedContacts()
      .filter(
        (c) =>
          c.contactName?.toLowerCase().includes(q) ||
          c.phones?.some((p) => p.value.includes(q))
      );

    console.log(`[ChatsPage] 🔍 Filtered ${this.filteredContacts.length} contacts`);
  }
}
