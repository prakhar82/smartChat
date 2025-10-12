/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * LoggerService
 * --------------------------------------------
 * Centralized logging utility for SmartChat.
 * - Automatically disables debug logs in production.
 * - Adds consistent context prefixes for traceability.
 * - Provides safe wrappers around console methods.
 */

import {Injectable} from '@angular/core';
import {environment} from '../../environments/environment';

@Injectable({providedIn: 'root'})
export class LoggerService {
  /** Whether debug logging is enabled (from environment) */
  private readonly debugEnabled = environment.enableDebugLogs;

  /**
   * Logs an informational message.
   * @param context Class or feature name
   * @param message Message to log
   * @param data Optional data to attach
   */
  info(context: string, message: string, data?: any): void {
    console.info(`%c[${context}] ℹ️ ${message}`, 'color:#1976d2; font-weight:bold;', data ?? '');
  }

  /** Logs a success or state message */
  success(context: string, message: string, data?: any): void {
    console.log(`%c[${context}] ✅ ${message}`, 'color:#4caf50; font-weight:bold;', data ?? '');
  }

  /** Logs a warning message */
  warn(context: string, message: string, data?: any): void {
    console.warn(`%c[${context}] ⚠️ ${message}`, 'color:#fbc02d; font-weight:bold;', data ?? '');
  }

  /** Logs an error message */
  error(context: string, message: string, data?: any): void {
    console.error(`%c[${context}] ❌ ${message}`, 'color:#e53935; font-weight:bold;', data ?? '');
  }

  /** Logs a debug message — only in dev mode */
  debug(context: string, message: string, data?: any): void {
    if (!this.debugEnabled) return;
    console.debug(`%c[${context}] 🐞 ${message}`, 'color:#9c27b0; font-weight:bold;', data ?? '');
  }
}
