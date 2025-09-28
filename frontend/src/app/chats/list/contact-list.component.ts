/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {FormsModule} from '@angular/forms';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {AppModalComponent} from '../../shared/modal/app-modal.component';

@Component({
  selector: 'app-contact-list',
  standalone: true,
  imports: [CommonModule, FormsModule, AppModalComponent],
  templateUrl: './contact-list.component.html',
  styleUrls: ['./contact-list.component.css'],
})
export class ContactListComponent implements OnInit, OnDestroy {
  contacts: MatchedContact[] = [];
  searchQuery = '';
  private destroy$ = new Subject<void>();

  showInvitePopup = false;
  selectedContact: MatchedContact | null = null;

  expandedContacts = new Set<string>();

  constructor(
    private router: Router,
    private contactService: ContactService,
    private cdRef: ChangeDetectorRef
  ) {
  }

  ngOnInit() {
    // Listen for updates
    this.contactService.contactsUpdated$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadFromCache());

    // First load
    this.loadFromCache();
  }

  private loadFromCache() {
    const cached = this.contactService.getCachedContacts();
    if (cached && cached.length > 0) {
      console.log('[ContactList] Loaded from cache:', cached);
      this.contacts = cached;
      this.cdRef.detectChanges();
    } else {
      // fallback to fresh fetch if no cache
      this.loadContacts(true);
    }
  }

  private loadContacts(force = false) {
    this.contactService.getMatchedContacts(force).subscribe({
      next: (list) => {
        console.log('[ContactList] Matched contacts:', list);
        this.contacts = list;
        this.cdRef.detectChanges();
      },
      error: (err) => {
        console.error('[ContactList] Failed to load contacts', err);
        this.contacts = [];
        this.cdRef.detectChanges();
      },
    });
  }

  filteredContacts() {
    const qRaw = (this.searchQuery || '').trim();
    if (!qRaw) return this.contacts;

    const qLower = qRaw.toLowerCase();
    const qDigits = qRaw.replace(/\D/g, '');

    return this.contacts.filter((c) => {
      if ((c.contactName || '').toLowerCase().includes(qLower)) return true;

      if (
        c.phones?.some((p) => {
          const phone = p.value || '';
          if (!phone) return false;
          if (qDigits.length > 0) {
            return phone.replace(/\D/g, '').includes(qDigits);
          }
          return phone.includes(qRaw);
        })
      )
        return true;

      if (c.emails?.some((e) => (e.value || '').toLowerCase().includes(qLower)))
        return true;

      return false;
    });
  }

  toggleExpand(c: MatchedContact, event: Event) {
    event.stopPropagation();
    const id = c.contactId ?? '';
    if (!id) return;

    if (this.expandedContacts.has(id)) {
      this.expandedContacts.delete(id);
    } else {
      this.expandedContacts.add(id);
    }
  }

  isExpanded(c: MatchedContact): boolean {
    const id = c.contactId ?? '';
    return id ? this.expandedContacts.has(id) : false;
  }

  openInvitePopup(c: MatchedContact, event: Event) {
    event.stopPropagation();
    this.selectedContact = c;
    this.showInvitePopup = true;
  }

  closeInvitePopup() {
    this.showInvitePopup = false;
    this.selectedContact = null;
  }

  sendInvite() {
    const email = this.selectedContact?.emails?.[0]?.value;
    if (email) {
      this.contactService
        .sendInviteEmail(email, this.selectedContact!.contactName)
        .subscribe({
          next: () => {
            alert('✅ Invite sent!');
            this.closeInvitePopup();
          },
          error: (err) => {
            console.error('[ContactList] Failed to send invite', err);
            alert('❌ Failed to send invite');
          },
        });
    }
  }

  openChat(c: MatchedContact) {
    if (c.registered && c.matchedUserId) {
      console.log('[ContactList] Opening chat with user:', c.matchedUserId);

      this.router.navigate([
        {
          outlets: {
            primary: ['chats'], // left panel always ContactList
            chat: [c.matchedUserId], // right panel ChatWindow
          },
        },
      ]);
    }
  }


  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
