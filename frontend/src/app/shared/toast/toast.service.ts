/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';

export interface ToastMessage {
  id: number;
  text: string;
  type: 'success' | 'error' | 'info';
  duration: number;
  direction?: 'bottom' | 'top' | 'left' | 'right'; // 👈 new
}


@Injectable({providedIn: 'root'})
export class ToastService {
  private messagesSubject = new BehaviorSubject<ToastMessage[]>([]);
  messages$ = this.messagesSubject.asObservable();

  private counter = 0;

  constructor() {
    console.log('[ToastService] ✅ Initialized');
  }

  show(
    text: string,
    type: 'success' | 'error' | 'info' = 'info',
    duration = 3000,
    direction: 'bottom' | 'top' | 'left' | 'right' = 'bottom'
  ): void {
    const message: ToastMessage = {
      id: ++this.counter,
      text,
      type,
      duration,
      direction,
    };

    const current = this.messagesSubject.value;
    this.messagesSubject.next([...current, message]);
    console.log(`[ToastService] 🌈 Toast (${type}, ${direction}): ${text}`);

    setTimeout(() => this.remove(message.id), duration);
  }


  remove(id: number): void {
    const current = this.messagesSubject.value;
    const target = document.querySelector(`.toast[data-id="${id}"]`);
    if (target) target.classList.add('removing');
    setTimeout(() => {
      this.messagesSubject.next(current.filter((m) => m.id !== id));
    }, 350);
  }


  clearAll(): void {
    this.messagesSubject.next([]);
  }
}
