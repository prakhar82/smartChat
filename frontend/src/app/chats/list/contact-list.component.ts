/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * 📇 ContactListComponent
 * ---------------------------------------------------------
 * Displays the SmartChat contact list.
 * - Reactively listens for contact updates (from ContactService)
 * - Reactively listens for presence updates (from PresenceService)
 * - Automatically updates online/offline UI
 * ---------------------------------------------------------
 */

import {ChangeDetectorRef, Component, EventEmitter, Input, NgZone, OnDestroy, OnInit, Output,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Subscription} from 'rxjs';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {ToastService} from '../../shared/toast/toast.service';
import {PresenceService} from '../../shared/presence/presence.service';
import {PresenceIndicatorComponent} from '../../shared/presence/presence-indicator.component';
import {LastSeenPipe} from '../../shared/presence/last-seen.pipe';

@Component({
  selector: 'app-contact-list',
  standalone: true,
  imports: [CommonModule, PresenceIndicatorComponent, LastSeenPipe],
  templateUrl: './contact-list.component.html',
  styleUrls: ['./contact-list.component.scss'],
})
export class ContactListComponent implements OnInit, OnDestroy {
  /** 🔹 Input contact array (fallback if no live stream) */
  @Input() contacts: MatchedContact[] = [];

  /** 🔹 Emits when a contact is selected */
  @Output() contactSelected = new EventEmitter<MatchedContact>();

  /** 🔹 Emits optional event for closing a chat */
  @Output() closeChat = new EventEmitter<void>();

  /** 🌀 Loading state for UI spinner */
  isLoading = true;

  /** 🧹 Subscription container */
  private subs = new Subscription();

  constructor(
    private readonly contactService: ContactService,
    private readonly presenceService: PresenceService,
    private readonly toastService: ToastService,
    private readonly cdRef: ChangeDetectorRef,
    private readonly zone: NgZone
  ) {
  }

  /* =========================================================
   * 🚀 Lifecycle
   * ========================================================= */
  ngOnInit(): void {
    console.log('[ContactList] 🚀 Initialized contact list');

    this.contactService.contacts$.subscribe((contacts) => {
      // Merge live presence data into contacts
      this.contacts = this.presenceService.mergeWithContacts(contacts);
    });


    // ✅ Subscribe to contacts list
    this.subs.add(
      this.contactService.contacts$.subscribe({
        next: (list: MatchedContact[]) => {
          this.zone.run(() => {
            this.isLoading = false;
            this.contacts = [...list];
            console.log(`[ContactList] ✅ Contacts loaded: ${list.length}`);
            this.cdRef.detectChanges();
          });
        },
        error: (err) => {
          this.isLoading = false;
          console.error('[ContactList] ❌ Error fetching contacts:', err);
          this.toastService.show('❌ Failed to load contacts', 'error', 3000, 'bottom');
        },
      })
    );

    // ✅ Subscribe to presence map (reactive live updates)
    // ✅ Subscribe to presence map (reactive live updates)
    this.subs.add(
      this.presenceService.getPresenceStream().subscribe((presenceMap) => {
        this.zone.run(() => {
          this.contacts.forEach((contact) => {
            const id = contact.matchedUserId ?? contact.contactId;
            if (!id) return;

            const presence = presenceMap.get(String(id));
            contact.online = presence?.online ?? false;
            contact.lastSeen = presence?.lastSeen ?? null;
          });
          this.cdRef.detectChanges();
        });
      })
    );

  }

  ngOnDestroy(): void {
    console.log('[ContactList] 🧹 Destroying and unsubscribing...');
    this.subs.unsubscribe();
  }

  /* =========================================================
   * 💬 Contact Selection
   * ========================================================= */
  onSelect(contact: MatchedContact): void {
    if (contact.registered) {
      console.log('[ContactList] 💬 Selected contact →', contact);
      this.contactSelected.emit(contact);
    } else {
      this.toastService.show(
        `${contact.contactName || 'This contact'} is not on SmartChat yet.`,
        'info',
        3000,
        'bottom'
      );
    }
  }

  /* =========================================================
   * ✉️ Invite Contact
   * ========================================================= */
  invite(contact: MatchedContact, event: Event): void {
    event.stopPropagation();
    const email = contact.emails?.[0]?.value;
    if (!email) {
      this.toastService.show(
        `No email found for ${contact.contactName || 'contact'}`,
        'error',
        3000,
        'bottom'
      );
      return;
    }

    console.log('[ContactList] 🚀 Sending invite to', email);
    this.toastService.show(`Sending invite to ${contact.contactName || email}...`, 'info', 2500, 'right');

    this.subs.add(
      this.contactService.sendInviteEmail(email, contact.contactName || 'Friend').subscribe({
        next: () => {
          this.toastService.show(`✅ Invite sent to ${contact.contactName || email}`, 'success', 4000, 'bottom');
        },
        error: (err) => {
          console.error(`[ContactList] ❌ Failed to send invite to ${email}:`, err);
          this.toastService.show(`❌ Failed to send invite`, 'error', 4000, 'left');
        },
      })
    );
  }

  /* =========================================================
   * ⚡ TrackBy Optimization
   * ========================================================= */
  trackById(index: number, contact: MatchedContact): string {
    return (contact.contactId ?? contact.matchedUserId ?? index).toString();
  }
}
