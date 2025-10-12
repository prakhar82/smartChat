/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Component, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ToastMessage, ToastService} from './toast.service';
import {Subscription} from 'rxjs';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './toast.component.html',
  styleUrls: ['./toast.component.css'],
})
export class ToastComponent implements OnInit, OnDestroy {
  messages: ToastMessage[] = [];
  private sub?: Subscription;

  constructor(private toastService: ToastService) {
  }

  ngOnInit(): void {
    this.sub = this.toastService.messages$.subscribe((msgs) => (this.messages = msgs));
  }

  remove(id: number): void {
    // Optional fade-out animation before removing
    const el = document.querySelector(`.toast-container .toast:nth-child(${id})`);
    if (el) {
      el.classList.add('removing');
      setTimeout(() => this.toastService.remove(id), 300);
    } else {
      this.toastService.remove(id);
    }
  }
  

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
