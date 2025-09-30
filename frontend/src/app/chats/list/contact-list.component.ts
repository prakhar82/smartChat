/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subscription} from 'rxjs';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {ChatsPageComponent} from '../page/chats-page.component';

@Component({
  selector: 'app-contact-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './contact-list.component.html',
  styleUrls: ['./contact-list.component.css'],
})
export class ContactListComponent implements OnInit, OnDestroy {
  @Input() parent?: ChatsPageComponent;

  contacts: MatchedContact[] = [];
  filtered: MatchedContact[] = [];

  searchQuery = ''; // ✅ fixed unresolved variable
  private sub?: Subscription;

  constructor(private contactService: ContactService) {
  }

  ngOnInit(): void {
    this.sub = this.contactService.contactsUpdated$.subscribe(() => {
      const list = this.contactService.getCachedContacts();
      this.contacts = list || [];
      this.applyFilter();
    });

    // load initial
    const list = this.contactService.getCachedContacts();
    this.contacts = list || [];
    this.applyFilter();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  // ✅ fixed unresolved method
  onSearch(): void {
    this.applyFilter();
  }

  private applyFilter(): void {
    const term = this.searchQuery.toLowerCase();
    if (!term) {
      this.filtered = [...this.contacts];
      return;
    }
    this.filtered = this.contacts.filter(
      (c) =>
        c.contactName.toLowerCase().includes(term) ||
        c.emails.some((e) => e.value.toLowerCase().includes(term)) ||
        c.phones.some((p) => p.value.includes(term))
    );
  }

  // ✅ now strictly typed
  openChat(contact: MatchedContact): void {
    if (contact.registered && this.parent) {
      this.parent.openChat(contact.matchedUserId!);
    } else if (contact.canInvite) {
      console.log('[ContactList] Invite link clicked for:', contact.contactName);
      this.contactService
        .sendInviteEmail(contact.emails[0]?.value || '', contact.contactName)
        .subscribe({
          next: () =>
            alert(`✅ Invite sent to ${contact.contactName} successfully.`),
          error: (err) => {
            console.error('[ContactList] ❌ Failed to send invite', err);
            alert(`❌ Failed to send invite: ${err?.error?.message || err}`);
          },
        });
    }
  }

  trackById(_: number, item: MatchedContact): string | null {
    return item.contactId;
  }
}
