/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {AfterViewChecked, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ChatService} from '../chat.service';

@Component({
  selector: 'app-chat-window',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-window.component.html',
  styleUrls: ['./chat-window.component.css'],
})
export class ChatWindowComponent implements OnInit, AfterViewChecked {
  contactId!: number;
  messages: any[] = [];
  newMessage = '';
  showTypingTooltip = false;
  truncatedMessage = '';

  typingTimeout: any;

  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  constructor(private route: ActivatedRoute, private chatService: ChatService) {
  }

  ngOnInit(): void {
    this.contactId = Number(this.route.snapshot.paramMap.get('id'));
    this.messages = [
      {senderId: 1, text: 'Hello'},
      {senderId: 2, text: 'Hi there'},
    ];
  }

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  private scrollToBottom() {
    try {
      this.messagesContainer.nativeElement.scrollTop =
        this.messagesContainer.nativeElement.scrollHeight;
    } catch (err) {
    }
  }

  sendMessage() {
    if (!this.newMessage.trim()) return;
    this.messages.push({senderId: 1, text: this.newMessage});
    this.newMessage = '';
    this.showTypingTooltip = false;
  }

  checkOverflow(event: any) {
    const textarea = event.target as HTMLTextAreaElement;
    textarea.style.height = 'auto';
    textarea.style.height = textarea.scrollHeight + 'px';

    if (!this.newMessage.trim()) {
      this.showTypingTooltip = false;
      clearTimeout(this.typingTimeout);
      return;
    }

    if (textarea.scrollHeight > 60) {
      this.showTypingTooltip = true;

      const screenWidth = window.innerWidth;
      let truncateLength = 50;
      if (screenWidth < 600) truncateLength = 20;
      else if (screenWidth < 1024) truncateLength = 35;

      this.truncatedMessage =
        this.newMessage.length > truncateLength
          ? this.newMessage.substring(0, truncateLength) + '...'
          : this.newMessage;

      clearTimeout(this.typingTimeout);
      this.typingTimeout = setTimeout(() => {
        this.showTypingTooltip = false;
      }, 3000);
    } else {
      this.showTypingTooltip = false;
      clearTimeout(this.typingTimeout);
    }
  }
}
