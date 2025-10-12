/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';

export type ThemeMode = 'light' | 'dark';

@Injectable({providedIn: 'root'})
export class ThemeService {
  private readonly STORAGE_KEY = 'smartchat.theme';
  private themeSubject = new BehaviorSubject<ThemeMode>('light');

  /** Reactive theme stream */
  theme$ = this.themeSubject.asObservable();

  constructor() {
    const saved = (localStorage.getItem(this.STORAGE_KEY) as ThemeMode) || this.detectSystemTheme();
    this.applyTheme(saved);
  }

  /** Current theme getter */
  get currentTheme(): ThemeMode {
    return this.themeSubject.value;
  }

  /** Toggle light/dark and save */
  toggleTheme(): void {
    const newTheme: ThemeMode = this.currentTheme === 'light' ? 'dark' : 'light';
    this.applyTheme(newTheme);
  }

  /** Apply and persist theme */
  private applyTheme(theme: ThemeMode): void {
    const body = document.body;
    body.classList.remove('light-mode', 'dark-mode');
    body.classList.add(theme === 'dark' ? 'dark-mode' : 'light-mode');
    localStorage.setItem(this.STORAGE_KEY, theme);
    this.themeSubject.next(theme);
    console.log(`[ThemeService] 🌗 Applied theme: ${theme}`);
  }

  /** Detect system-preferred theme */
  private detectSystemTheme(): ThemeMode {
    try {
      const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      return prefersDark ? 'dark' : 'light';
    } catch {
      return 'light';
    }
  }
}
