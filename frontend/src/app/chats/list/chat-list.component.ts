/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, Input, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Router} from '@angular/router';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {AuthService} from '../../auth/auth.service';
import {ChatService, RecentChat} from '../chat.service'; // 👈 import RecentChat

@Component({
  selector: 'app-chat-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-list.component.html',
  styleUrls: ['./chat-list.component.css']
})
export class ChatListComponent implements OnInit {

  @Input() userId!: number;
  // ✅ strongly typed arrays
  recentChats: RecentChat[] = [];
  registeredContacts: MatchedContact[] = [];
  unregisteredContacts: MatchedContact[] = [];

  // ✅ filtered versions
  filteredRecent: RecentChat[] = [];
  filteredRegistered: MatchedContact[] = [];
  filteredUnregistered: MatchedContact[] = [];

  searchQuery = '';
  loading = false;
  error = '';

  constructor(
    private contactService: ContactService,
    private auth: AuthService,
    private chatService: ChatService,   // 👈 inject ChatService
    private router: Router
  ) {
  }

  ngOnInit(): void {
    this.userId = this.auth.getUserId();
    if (!this.userId) {
      this.error = 'User not logged in';
      return;
    }

    this.loadContacts();
    this.loadRecentChats();
  }

  private loadContacts() {
    this.loading = true;
    this.contactService.getMatchedContacts(this.userId).subscribe({
      next: (data: MatchedContact[]) => {
        this.registeredContacts = data.filter(c => c.registered);
        this.unregisteredContacts = data.filter(c => !c.registered);

        // initialize filtered lists
        this.filteredRegistered = [...this.registeredContacts];
        this.filteredUnregistered = [...this.unregisteredContacts];
        this.loading = false;
      },
      error: (err: any) => {   // 👈 fix
        console.error('Failed to load contacts', err);
        this.error = 'Failed to load contacts';
        this.loading = false;
      }
    });
  }

  private loadRecentChats() {
    this.chatService.getRecentChats(this.userId).subscribe({
      next: (chats: RecentChat[]) => {
        this.recentChats = chats;
        this.filteredRecent = [...chats]; // ✅ strong type
      },
      error: (err: any) => {   // 👈 fix
        console.error('Failed to load recent chats', err);
        this.error = 'Failed to load recent chats';
      }
    });
  }

  filterContacts() {
    const q = this.searchQuery.toLowerCase().trim();

    this.filteredRegistered = this.registeredContacts.filter(c =>
      (c.contactName?.toLowerCase().includes(q)) ||
      (c.phoneNormalized?.includes(q))
    );

    this.filteredUnregistered = this.unregisteredContacts.filter(c =>
      (c.contactName?.toLowerCase().includes(q)) ||
      (c.phoneNormalized?.includes(q))
    );

    this.filteredRecent = this.recentChats.filter(c =>
      (c.contactName?.toLowerCase().includes(q)) ||
      (c.phoneNormalized?.includes(q))
    );
  }

  openChat(contact: MatchedContact | RecentChat) {
    if (contact.contactId) {
      this.router.navigate(['/chats', contact.contactId]);
    }
  }

  sendInvite(contact: MatchedContact) {
    if (!contact.email) {
      alert('This contact has no email saved.');
      return;
    }

    this.contactService.sendInviteEmail(contact.email, contact.contactName).subscribe({
      next: () => alert(`Invite sent to ${contact.contactName}`),
      error: (e: any) => {
        console.error('Error sending invite', e);
        this.error = 'Failed to send invite.';
      }
    });
  }
}
