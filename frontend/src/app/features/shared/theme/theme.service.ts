/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * 🌗 ThemeService
 * ---------------------------------------------------------
 * Centralized control for SmartChat light/dark mode.
 * - Persists preference in localStorage
 * - Syncs with system theme
 * - Reactively updates via RxJS
 * - Automatically updates `data-theme` on <html> root
 * ---------------------------------------------------------
 */

import {Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';

export type ThemeMode = 'light' | 'dark';

@Injectable({providedIn: 'root'})
export class ThemeService {
  private readonly STORAGE_KEY = 'smartchat.theme';
  private themeSubject = new BehaviorSubject<ThemeMode>('light');
  readonly theme$ = this.themeSubject.asObservable();

  constructor() {
    const saved = this.getSavedTheme() ?? this.detectSystemTheme();
    this.setTheme(saved, false);

    // Optional: Sync when OS-level preference changes
    if (window.matchMedia) {
      const media = window.matchMedia('(prefers-color-scheme: dark)');
      media.addEventListener('change', (event) => {
        if (!this.hasUserPreference()) {
          const systemTheme: ThemeMode = event.matches ? 'dark' : 'light';
          this.setTheme(systemTheme, false);
        }
      });
    }
  }

  /** Get the current theme */
  get currentTheme(): ThemeMode {
    return this.themeSubject.value;
  }

  /** Toggle theme manually */
  toggleTheme(): void {
    const newTheme: ThemeMode = this.currentTheme === 'light' ? 'dark' : 'light';
    this.setTheme(newTheme, true);
  }

  /** Explicitly set theme */
  setTheme(theme: ThemeMode, persist = true): void {
    this.themeSubject.next(theme);
    document.documentElement.setAttribute('data-theme', theme);
    document.body.classList.remove('light-mode', 'dark-mode');
    document.body.classList.add(`${theme}-mode`);
    if (persist) {
      localStorage.setItem(this.STORAGE_KEY, theme);
    }
    console.log(`[ThemeService] 🌗 Theme applied: ${theme}`);
  }

  /** Detects if user manually chose a theme */
  private hasUserPreference(): boolean {
    return !!localStorage.getItem(this.STORAGE_KEY);
  }

  /** Reads from storage safely */
  private getSavedTheme(): ThemeMode | null {
    try {
      const saved = localStorage.getItem(this.STORAGE_KEY) as ThemeMode | null;
      return saved === 'light' || saved === 'dark' ? saved : null;
    } catch {
      return null;
    }
  }

  /** Detect system default */
  private detectSystemTheme(): ThemeMode {
    try {
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      return prefersDark ? 'dark' : 'light';
    } catch {
      return 'light';
    }
  }
}
