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
import {combineLatest, Subscription} from 'rxjs';
import {distinctUntilChanged, first} from 'rxjs/operators';
import {ContactService, MatchedContact} from '../../contacts/contact.service';
import {ToastService} from '../../shared/toast/toast.service';
import {PresenceService} from '../../shared/presence/presence.service';
import {PresenceIndicatorComponent} from '../../shared/presence/presence-indicator.component';
import {LastSeenPipe} from '../../shared/presence/last-seen.pipe';
import {environment} from '../../../../environments/environment';
import {PresenceLegendComponent} from '../../shared/presence/presence-legend.component';

@Component({
  selector: 'app-contact-list',
  standalone: true,
  imports: [CommonModule, PresenceIndicatorComponent, LastSeenPipe, PresenceLegendComponent],
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

  /** ✅ Tracks last known presenceMap (for deep comparison) */
  private lastPresenceMap: Map<string, any> | null = null;

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

    // ✅ Combine contact and presence streams reactively
    const combined$ = combineLatest([
      this.contactService.contacts$,
      this.presenceService.getPresenceStream().pipe(
        distinctUntilChanged((prev, curr) => !this.hasPresenceChanged(curr))
      ),
    ]);

    this.subs.add(
      combined$.subscribe({
        next: ([contacts, presenceMap]) => {
          this.zone.run(() => {
            this.isLoading = false;

            // 💡 Merge presence only if map changed
            if (this.hasPresenceChanged(presenceMap)) {
              this.lastPresenceMap = new Map(presenceMap);
              this.contacts = this.presenceService
                .mergeWithContacts(contacts)
                .map((c) => ({
                  ...c,
                  registered: c.registered || !!c.matchedUserId,
                }));
              if (!environment.production && environment.enableDebugLogs) {
                console.log(
                  `[ContactList] 🔄 Contacts merged with live presence → ${this.contacts.length} entries`
                );
              }
            } else {
              this.contacts = [...contacts];
            }
          });
        },
        error: (err) => {
          this.isLoading = false;
          console.error('[ContactList] ❌ Error fetching contacts/presence:', err);
          this.toastService.show(
            '❌ Failed to load contacts or presence',
            'error',
            3000,
            'bottom'
          );
        },
      })
    );
  }

  /** 🧠 Compare if presenceMap actually changed (for perf) */
  private hasPresenceChanged(newMap: Map<string, any>): boolean {
    if (!this.lastPresenceMap) return true;
    if (this.lastPresenceMap.size !== newMap.size) return true;

    for (const [key, value] of newMap) {
      const prev = this.lastPresenceMap.get(key);
      if (
        !prev ||
        prev.online !== value.online ||
        (prev.lastSeen &&
          value.lastSeen &&
          prev.lastSeen.toString() !== value.lastSeen.toString())
      ) {
        return true;
      }
    }
    return false;
  }

  /* =========================================================
   * 💬 Contact Selection
   * ========================================================= */
  onSelect(contact: MatchedContact): void {
    if (contact.registered) {
      if (!environment.production && environment.enableDebugLogs) {
        console.log('[ContactList] 💬 Selected contact →', contact);
      }
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
    this.toastService.show(
      `Sending invite to ${contact.contactName || email}...`,
      'info',
      2500,
      'right'
    );

    this.subs.add(
      this.contactService
        .sendInviteEmail(email, contact.contactName || 'Friend')
        .pipe(first())
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

  /* =========================================================
   * 🧹 Cleanup
   * ========================================================= */
  ngOnDestroy(): void {
    console.log('[ContactList] 🧹 Destroying and unsubscribing...');
    this.subs.unsubscribe();
    this.lastPresenceMap = null;
  }
}
