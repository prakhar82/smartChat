/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * 🧩 Left Sidebar Component
 * ----------------------------------------
 * Displays the SmartChat sidebar with:
 *  - Profile and status selector
 *  - Quick access icons (profile, chats)
 *  - Google Contacts sync
 *  - Theme toggle (dark/light)
 * ----------------------------------------
 */

import {Component, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subscription} from 'rxjs';
import {ContactService} from '../../contacts/contact.service';
import {ThemeMode, ThemeService} from '../../shared/theme/theme.service';
import {ToastService} from '../../shared/toast/toast.service';

@Component({
  selector: 'app-left-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './left-sidebar.component.html',
  styleUrls: ['./left-sidebar.component.scss'],
})
export class LeftSidebarComponent implements OnInit, OnDestroy {
  /* =========================================================
   * 👤 User Info
   * ========================================================= */
  firstName = 'User'; // ✅ fixed — now defined property for HTML
  userStatus: 'available' | 'busy' | 'away' = 'available';

  /* =========================================================
   * 🎨 Theme & UI
   * ========================================================= */
  theme: ThemeMode = 'light';
  isSyncing = false;

  /* =========================================================
   * 🔔 Toast Handling
   * ========================================================= */
  showToast = false;
  toastMessage = '';
  toastType: 'success' | 'error' | 'info' = 'info';

  private subs: Subscription[] = [];

  constructor(
    private contactService: ContactService,
    private themeService: ThemeService,
    private toastService: ToastService
  ) {
  }

  /* =========================================================
   * 🧭 Lifecycle Hooks
   * ========================================================= */
  ngOnInit(): void {
    console.log('[LeftSidebar] 🚀 Initialized');

    // ✅ Sync theme changes
    const themeSub = this.themeService.theme$.subscribe((mode) => (this.theme = mode));
    this.subs.push(themeSub);

    // ✅ Load user info safely from localStorage
    try {
      const userData = localStorage.getItem('user');
      if (userData) {
        const parsed = JSON.parse(userData);
        this.firstName = parsed.firstName || parsed.name?.split(' ')[0] || 'User';
      }
    } catch {
      this.firstName = 'User';
    }
  }

  ngOnDestroy(): void {
    this.subs.forEach((sub) => sub.unsubscribe());
  }

  /* =========================================================
   * 🌗 Theme Toggle
   * ========================================================= */
  onToggleTheme(): void {
    this.themeService.toggleTheme();
    const nextMode = this.theme === 'dark' ? 'light' : 'dark';
    this.toastService.show(`🌗 Switched to ${nextMode} mode`, 'info', 2500, 'bottom');
  }

  /* =========================================================
   * 🔄 Google Contacts Sync
   * ========================================================= */
  requestGoogleSync(): void {
    if (this.isSyncing) return;

    this.isSyncing = true;
    this.showLocalToast('🔄 Syncing Google contacts...', 'info');

    const syncSub = this.contactService.syncGoogleContacts().subscribe({
      next: (contacts: any[]) => {
        this.isSyncing = false;
        this.showLocalToast(`✅ Synced ${contacts.length} contacts`, 'success');
      },
      error: (err: any) => {
        console.error('[LeftSidebar] ❌ Google sync failed:', err);
        this.isSyncing = false;
        this.showLocalToast('❌ Failed to sync Google contacts', 'error');
      },
    });

    this.subs.push(syncSub);
  }

  /* =========================================================
   * 🧭 Navigation
   * ========================================================= */
  goToChats(): void {
    console.log('[LeftSidebar] Navigating to chats...');
  }

  /* =========================================================
   * 🟢 Status Selector
   * ========================================================= */
  changeStatus(newStatus: 'available' | 'busy' | 'away'): void {
    this.userStatus = newStatus;
    this.showLocalToast(`Status set to ${newStatus}`, 'info');
  }

  /* =========================================================
   * 🔔 Local Toast Helper
   * ========================================================= */
  private showLocalToast(
    message: string,
    type: 'success' | 'error' | 'info' = 'info'
  ): void {
    this.toastMessage = message;
    this.toastType = type;
    this.showToast = true;

    setTimeout(() => (this.showToast = false), 3000);
  }
}
