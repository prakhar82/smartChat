/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Router} from '@angular/router';
import {Subscription} from 'rxjs';

import {ContactService} from '../../contacts/contact.service';
import {ThemeMode, ThemeService} from '../../shared/theme/theme.service';
import {ToastService} from '../../shared/toast/toast.service';
import {PresenceLegendComponent} from '../../shared/presence/presence-legend.component';

@Component({
  selector: 'app-left-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule, PresenceLegendComponent],
  templateUrl: './left-sidebar.component.html',
  styleUrls: ['./left-sidebar.component.scss'],
})
export class LeftSidebarComponent implements OnInit, OnDestroy {
  /* =========================================================
   * 👤 User Info
   * ========================================================= */
  firstName = 'User';
  userStatus: 'available' | 'busy' | 'away' = 'available';

  /* =========================================================
   * 🎨 Theme & UI
   * ========================================================= */
  theme: ThemeMode = 'light';
  isSyncing = false;
  activeTab: 'chats' | 'contacts' | 'profile' = 'chats';
  isPinned = false;
  isHovered = false;
  pinGlow = false;
  showShimmer = false; // ✅ NEW shimmer flag

  /* =========================================================
   * 🔔 Toast Handling
   * ========================================================= */
  showToast = false;
  toastMessage = '';
  toastType: 'success' | 'error' | 'info' = 'info';

  private subs: Subscription[] = [];

  constructor(
    private readonly contactService: ContactService,
    private readonly themeService: ThemeService,
    private readonly toastService: ToastService,
    private readonly router: Router
  ) {
  }

  /* =========================================================
   * 🚀 Lifecycle
   * ========================================================= */
  ngOnInit(): void {
    console.log('[LeftSidebar] 🚀 Initialized');

    const themeSub = this.themeService.theme$.subscribe(
      (mode) => (this.theme = mode)
    );
    this.subs.push(themeSub);

    // ✅ Load saved user data
    try {
      const userData = localStorage.getItem('user');
      if (userData) {
        const parsed = JSON.parse(userData);
        this.firstName = parsed.firstName || parsed.name?.split(' ')[0] || 'User';
      }
    } catch {
      this.firstName = 'User';
    }

    // ✅ Restore pinned state
    const pinned = localStorage.getItem('sidebar_pinned');
    this.isPinned = pinned === 'true';

    // ✅ Load shimmer state (play shimmer only if not yet played)
    const shimmerPlayed = localStorage.getItem('sidebar_shimmer_played');
    this.showShimmer = shimmerPlayed !== 'true';
  }

  ngOnDestroy(): void {
    this.subs.forEach((s) => s.unsubscribe());
  }

  /* =========================================================
   * 📌 Toggle Pin
   * ========================================================= */
  togglePin(): void {
    this.isPinned = !this.isPinned;
    localStorage.setItem('sidebar_pinned', String(this.isPinned));

    // 🟢 Trigger glow
    this.pinGlow = true;
    setTimeout(() => (this.pinGlow = false), 1200);

    // ✨ Trigger shimmer ONCE (first time ever pinned)
    if (this.isPinned && this.showShimmer) {
      const shimmerOverlay = document.querySelector('.left-sidebar');
      shimmerOverlay?.classList.add('shimmer');

      // Mark shimmer as played
      localStorage.setItem('sidebar_shimmer_played', 'true');
      this.showShimmer = false;

      // Clean up class after animation
      setTimeout(() => shimmerOverlay?.classList.remove('shimmer'), 1500);
    }

    this.showLocalToast(
      this.isPinned ? '📌 Sidebar pinned' : '📍 Sidebar unpinned',
      'info'
    );
  }

  /* =========================================================
   * 🌗 Theme Toggle
   * ========================================================= */
  onToggleTheme(): void {
    this.themeService.toggleTheme();
    const nextMode = this.theme === 'dark' ? 'light' : 'dark';
    this.toastService.show(
      `🌗 Switched to ${nextMode} mode`,
      'info',
      2500,
      'bottom'
    );
  }

  /* =========================================================
   * 🔄 Google Sync
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
   * 🟢 Status Selector
   * ========================================================= */
  changeStatus(newStatus: 'available' | 'busy' | 'away'): void {
    this.userStatus = newStatus;
    this.showLocalToast(`🟢 Status: ${newStatus}`, 'info');
  }

  /* =========================================================
   * 🧭 Navigation
   * ========================================================= */
  setActiveTab(tab: 'chats' | 'contacts' | 'profile'): void {
    this.activeTab = tab;
    localStorage.setItem('sidebar_active_tab', tab);
  }

  goToChats() {
    this.router.navigate(['/chats']);
  }

  goToContacts() {
    this.router.navigate(['/contacts']);
  }

  goToProfile() {
    this.router.navigate(['/profile']);
  }

  /* =========================================================
   * 🔔 Toast Helper
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

  /* =========================================================
   * 🖱️ Hover Handlers
   * ========================================================= */
  onMouseEnter(): void {
    if (!this.isPinned) this.isHovered = true;
  }

  onMouseLeave(): void {
    if (!this.isPinned) this.isHovered = false;
  }
}
