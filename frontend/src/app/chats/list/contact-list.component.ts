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
 * - Subscribes to live contact updates from ContactService.
 * - Handles selection, invitation, and presence display.
 * - Emits contactSelected → for ChatsPageComponent (open chat).
 * - Fixes delayed refresh using ChangeDetectorRef/NgZone.
 * ---------------------------------------------------------
 */

import {ChangeDetectorRef, Component, EventEmitter, Input, NgZone, OnDestroy, OnInit, Output,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Subscription} from 'rxjs';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {ToastService} from '../../shared/toast/toast.service';
import {PresenceIndicatorComponent} from '../../shared/presence/presence-indicator.component';

@Component({
  selector: 'app-contact-list',
  standalone: true,
  imports: [CommonModule, PresenceIndicatorComponent],
  templateUrl: './contact-list.component.html',
  styleUrls: ['./contact-list.component.scss'],
})
export class ContactListComponent implements OnInit, OnDestroy {
  /** 🔹 Input contact array from parent (fallback if no stream) */
  @Input() contacts: MatchedContact[] = [];

  /** 🔹 Emits when a contact is selected (for ChatsPage → openChat) */
  @Output() contactSelected = new EventEmitter<MatchedContact>();

  /** 🔹 Emits optional event for closing a chat (prevents template error) */
  @Output() closeChat = new EventEmitter<void>();

  /** 🌀 Loading state for UI spinner */
  isLoading = true;

  /** 🧹 Subscription container for cleanup */
  private subs = new Subscription();

  constructor(
    private readonly contactService: ContactService,
    private readonly toastService: ToastService,
    private readonly cdRef: ChangeDetectorRef,
    private readonly zone: NgZone
  ) {
  }

  /* =========================================================
   * 🚀 Lifecycle Hooks
   * ========================================================= */
  ngOnInit(): void {
    console.log('[ContactList] 🚀 Initialized contact list');

    // ✅ Subscribe to reactive contact stream
    this.subs.add(
      this.contactService.contacts$.subscribe({
        next: (list: MatchedContact[]) => {
          this.zone.run(() => {
            this.isLoading = false;
            if (list?.length) {
              this.contacts = [...list];
              console.log(`[ContactList] ✅ Updated contact list: ${list.length}`);
            } else {
              console.warn('[ContactList] ⚠️ No contacts yet (waiting for fetch)');
            }
            this.cdRef.detectChanges();
          });
        },
        error: (err) => {
          this.isLoading = false;
          console.error('[ContactList] ❌ Error in contact stream:', err);
          this.toastService.show('❌ Failed to load contacts', 'error', 3000, 'bottom');
        },
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
      console.log('[ContactList] 💬 Selected registered contact →', contact);
      this.contactSelected.emit(contact); // ✅ emits to ChatsPageComponent
    } else {
      console.warn('[ContactList] ⚠️ Unregistered contact clicked →', contact);
      this.toastService.show(
        `${contact.contactName || 'This contact'} is not on SmartChat yet.`,
        'info',
        3000,
        'bottom'
      );
    }
  }

  /* =========================================================
   * ✉️ Invite Unregistered Contact
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
    this.toastService.show(
      `Sending invite to ${contact.contactName || email}...`,
      'info',
      2500,
      'right'
    );

    this.subs.add(
      this.contactService
        .sendInviteEmail(email, contact.contactName || 'Friend')
        .subscribe({
          next: () => {
            this.toastService.show(
              `✅ Invite sent to ${contact.contactName || email}`,
              'success',
              4000,
              'bottom'
            );
          },
          error: (err) => {
            console.error(`[ContactList] ❌ Failed to send invite to ${email}:`, err);
            this.toastService.show(
              `❌ Failed to send invite to ${contact.contactName || email}`,
              'error',
              4000,
              'left'
            );
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
