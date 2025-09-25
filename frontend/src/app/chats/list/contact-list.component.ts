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
    // 🔹 On updates, just pull from cached list
    this.contactService.contactsUpdated$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadFromCache());

    // 🔹 First load also from cache
    this.loadFromCache();
  }

  private loadFromCache() {
    const cached = this.contactService.getCachedContacts();
    if (cached) {
      console.log('Contacts loaded from cache:', cached);
      this.contacts = cached;
      this.cdRef.detectChanges();
    }
  }


  private loadContacts(force = false) {
    this.contactService.getMatchedContacts(force).subscribe({
      next: (list) => {
        console.log('Matched contacts received:', list);
        this.contacts = list;
        this.cdRef.detectChanges();
      },
      error: (err) => {
        console.error('Failed to load contacts', err);
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

    return this.contacts.filter(c => {
      if ((c.contactName || '').toLowerCase().includes(qLower)) return true;

      if (c.phones?.some(p => {
        const phone = (p.value || '').toString();
        if (!phone) return false;
        if (qDigits.length > 0) {
          return phone.replace(/\D/g, '').includes(qDigits);
        }
        return phone.includes(qRaw);
      })) return true;

      if (c.emails?.some(e => (e.value || '').toLowerCase().includes(qLower))) return true;

      return false;
    });
  }

  toggleExpand(c: MatchedContact, event: Event) {
    event.stopPropagation();
    const id = c.contactId ?? ''; // fallback empty string
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
      this.contactService.sendInviteEmail(email, this.selectedContact!.contactName).subscribe({
        next: () => {
          alert('Invite sent!');
          this.closeInvitePopup();
        },
        error: (err) => {
          console.error('Failed to send invite', err);
          alert('Failed to send invite');
        },
      });
    }
  }

  openChat(c: MatchedContact) {
    const registeredPhone = c.phones?.find((p) => p.registered);
    if (registeredPhone) {
      this.router.navigate(['/chats', c.contactId]);
    }
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
