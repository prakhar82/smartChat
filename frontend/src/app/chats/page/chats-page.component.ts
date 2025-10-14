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
export class ChatsPageComponent implements OnInit, OnDestroy {
  /** 👤 User Info */
  firstName = '';
  userId?: number;
  isDarkTheme = false;
  userStatus: 'available' | 'away' = 'available';
  private lastActivity = new Date();

  /** 📇 Contacts & Search */
  contacts: MatchedContact[] = [];
  filteredContacts: MatchedContact[] = [];
  searchQuery = '';

  /** 💬 UI States */
  selectedContactId?: number;
  selectedContactInfo?: MatchedContact;
  isMobileView = false;

  private subs = new Subscription(); // ✅ manage subscriptions

  constructor(
    private readonly userService: UserService,
    private readonly contactService: ContactService,
    private readonly chatService: ChatService
  ) {
  }

  /* =========================================================
   * 🚀 Lifecycle
   * ========================================================= */
  ngOnInit(): void {
    console.log('[ChatsPage] 🚀 Initialized');
    this.initUserAndConnect(); // ✅ merged loadUserProfile + connect logic
    this.loadContacts();
    this.startIdleWatcher();
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  /* =========================================================
   * 👤 Load User & Connect STOMP (reactively)
   * ========================================================= */
  private initUserAndConnect(): void {
    const sub = this.userService
      .getUserProfile()
      .subscribe({
        next: (user) => {
          this.firstName = user?.firstName || 'User';
          this.userId = user?.id ?? user?.userId ?? undefined;

          if (this.userId) {
            console.log(`[ChatsPage] 👤 User profile loaded: ${this.firstName} (ID: ${this.userId})`);

            // ✅ Only connect STOMP once, after valid ID is confirmed
            console.log('[ChatsPage] 🔌 Connecting STOMP for user:', this.userId);
            this.chatService.connectStomp(String(this.userId));
          } else {
            console.warn('[ChatsPage] ⚠️ No valid userId found, STOMP not connected');
          }
        },
        error: (err) => console.error('[ChatsPage] ❌ Failed to load profile:', err),
      });

    this.subs.add(sub);
  }

  /* =========================================================
   * 📇 Contacts
   * ========================================================= */
  private loadContacts(): void {
    console.log('[ChatsPage] 📥 Loading contacts...');
    const sub = this.contactService.getMatchedContacts().subscribe({
      next: (list) => {
        this.contacts = list;
        this.filteredContacts = list;
        console.log(`[ChatsPage] ✅ Loaded ${list.length} contacts`);
      },
      error: (err) => console.error('[ChatsPage] ❌ Failed to fetch contacts:', err),
    });
    this.subs.add(sub);
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
      const phone = c.phones?.map((p) => p.value?.toLowerCase()).join(' ') || '';
      return name.includes(term) || phone.includes(term);
    });

    console.log(`[ChatsPage] 🔍 Filtered ${this.filteredContacts.length} results`);
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
    if (this.userStatus !== 'available') {
      console.log('[ChatsPage] ✅ User active again');
    }
    this.userStatus = 'available';
    this.lastActivity = new Date();
  }

  /* =========================================================
   * 🎨 Theme
   * ========================================================= */
  toggleTheme(): void {
    this.isDarkTheme = !this.isDarkTheme;
    console.log('[ChatsPage] 🎨 Theme switched to', this.isDarkTheme ? 'Dark' : 'Light');
  }

  /* =========================================================
   * 💬 Chat Actions
   * ========================================================= */
  openChat(contact: MatchedContact): void {
    this.selectedContactId = Number(contact?.matchedUserId || contact?.contactId);
    this.selectedContactInfo = contact;
    console.log('[ChatsPage] 💬 Opened chat with:', contact.contactName);
  }

  closeChat(): void {
    console.log('[ChatsPage] ⬅️ Closed chat window');
    this.selectedContactId = undefined;
    this.selectedContactInfo = undefined;
  }
}
