import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService, ChatMessage } from '../chat.service';
import {window} from 'rxjs';

@Component({
  selector: 'app-chat-window',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-window.component.html',
  styleUrls: ['./chat-window.component.css']
})
export class ChatWindowComponent implements OnInit {
  contactId!: number;
  userId = Number(localStorage.getItem('userId')) || null;

  messages: ChatMessage[] = [];
  newMessage = '';

  // Reply
  replyTo: ChatMessage | null = null;

  // File preview
  selectedFile: File | null = null;
  filePreviewUrl: string | null = null;
  uploading = false;

  // Context menu
  contextMenuVisible = false;
  contextMenuX = 0;
  contextMenuY = 0;
  selectedMsg: ChatMessage | null = null;

  constructor(
    private route: ActivatedRoute,
    private chatService: ChatService
  ) {}

  ngOnInit(): void {
    this.contactId = Number(this.route.snapshot.paramMap.get('id'));
    if (this.userId) {
      this.chatService.connectWebSocket(this.userId);
      this.chatService.messages$.subscribe(msg => {
        if (
          msg &&
          ((msg.senderId === this.contactId && msg.receiverId === this.userId) ||
            (msg.senderId === this.userId && msg.receiverId === this.contactId))
        ) {
          this.messages.unshift(msg);
        }
      });
    }
    this.loadHistory();

    document.addEventListener('click', () => (this.contextMenuVisible = false));
  }

  loadHistory(): void {
    if (!this.userId) return;
    this.chatService.getChatHistory(this.contactId, this.userId).subscribe({
      next: data => (this.messages = data.reverse()),
      error: err => console.error('Failed to fetch messages', err),
    });
  }

  sendMessage(): void {
    if (!this.newMessage.trim() || !this.userId) return;

    const msg: ChatMessage = {
      senderId: this.userId,
      receiverId: this.contactId,
      message: this.replyTo ? `↩️ ${this.replyTo.message}\n${this.newMessage}` : this.newMessage,
      timestamp: new Date().toISOString(),
      status: 'SENT',
    };

    this.chatService.sendMessage(msg.senderId, msg.receiverId, msg.message!);
    this.messages.unshift(msg);
    this.newMessage = '';
    this.replyTo = null;
  }

  // ✅ File handling
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
      if (
        this.selectedFile.type.startsWith('image/') ||
        this.selectedFile.type.startsWith('video/')
      ) {
        this.filePreviewUrl = URL.createObjectURL(this.selectedFile);
      } else {
        this.filePreviewUrl = null;
      }
    }
  }

  cancelPreview(): void {
    this.selectedFile = null;
    this.filePreviewUrl = null;
  }

  sendFile(): void {
    if (!this.selectedFile || !this.userId) return;
    this.uploading = true;
    this.chatService.uploadFile(this.userId, this.contactId, this.selectedFile).subscribe({
      next: msg => {
        this.messages.unshift(msg);
        this.cancelPreview();
        this.uploading = false;
      },
      error: err => {
        console.error('Upload failed', err);
        this.uploading = false;
      },
    });
  }

  // ✅ Context menu
  openContextMenu(event: MouseEvent, msg: ChatMessage): void {
    event.preventDefault();
    this.contextMenuVisible = true;
    this.contextMenuX = event.clientX;
    this.contextMenuY = event.clientY;
    this.selectedMsg = msg;
  }

  copyMessage(): void {
    if (this.selectedMsg?.message) {
      navigator.clipboard.writeText(this.selectedMsg.message);
      alert('📋 Message copied!');
    }
    this.contextMenuVisible = false;
  }

  forwardMessage(): void {
    if (this.selectedMsg) {
      this.newMessage = this.selectedMsg.message || '';
    }
    this.contextMenuVisible = false;
  }

  deleteMessage(): void {
    if (this.selectedMsg) {
      this.messages = this.messages.filter(m => m !== this.selectedMsg);
    }
    this.contextMenuVisible = false;
  }

  // ✅ Swipe actions (mobile)
  onSwipeLeft(msg: ChatMessage): void {
    this.replyTo = msg;
  }

  onSwipeRight(msg: ChatMessage): void {
    this.messages = this.messages.filter(m => m !== msg);
  }

  // ✅ Delivery/Read status icon
  getStatusIcon(msg: ChatMessage): string {
    if (msg.status === 'READ') return '👁'; // eye open
    if (msg.status === 'DELIVERED') return '👁‍🗨'; // eye closed
    return '✔✔'; // sent
  }

  protected readonly window = window;

  isImage(fileUrl: string | undefined): boolean {
    return !!fileUrl && /\.(jpg|jpeg|png|gif)$/i.test(fileUrl);
  }

  isVideo(fileUrl: string | undefined): boolean {
    return !!fileUrl && /\.(mp4|webm)$/i.test(fileUrl);
  }

  openFile(fileUrl: string | undefined) {
    // Check if URL is provided and valid
    if (!fileUrl || fileUrl.trim() === '') {
      console.warn('No file URL provided to open.');
      return;
    }

    // Safely open the file in a new browser tab
    globalThis.open(fileUrl, '_blank');
  }
}
