/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable} from '@angular/core';
import {HttpClient, HttpEventType, HttpRequest} from '@angular/common/http';
import {BehaviorSubject, map, Observable} from 'rxjs';
import {Client, IMessage} from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import {filter} from 'rxjs/operators';
import imageCompression from 'browser-image-compression';

export interface ChatMessage {
  id?: number;
  senderId: number;
  receiverId: number;
  message?: string | null;
  fileUrl?: string | null;
  fileName?: string | null;
  emoji?: string | null;
  timestamp: string;
  status?: 'SENT' | 'DELIVERED' | 'READ';

  // system fields
  type?: 'MESSAGE' | 'DELETE';
  messageId?: number;
}

export interface RecentChat {
  contactId: string;
  contactName: string;
  phoneNormalized: string;
  registered: boolean;
  lastMessage: string;
  lastMessageTime: string;
}

@Injectable({
  providedIn: 'root',
})
export class ChatService {
  private stompClient?: Client;

  private messagesSubject = new BehaviorSubject<ChatMessage | null>(null);
  messages$ = this.messagesSubject.asObservable();

  private connectionSubject = new BehaviorSubject<boolean>(false);
  connection$ = this.connectionSubject.asObservable();

  constructor(private http: HttpClient) {
  }

  /**
   * Establish WebSocket (STOMP over SockJS) connection
   */
  connectWebSocket(userId: number): void {
    if (this.stompClient?.active) {
      console.log('[ChatService] ⚠️ WebSocket already active, skipping re-init');
      return;
    }

    console.log('[ChatService] 🚀 Connecting WebSocket for userId=', userId);
    const socketUrl = '/ws-chat';
    const socket = new SockJS(socketUrl);

    this.stompClient = new Client({
      webSocketFactory: () => socket as any,
      reconnectDelay: 5000, // retry after 5s
      debug: (str) => console.log('[STOMP DEBUG]', str),
    });

    this.stompClient.onConnect = () => {
      console.log('[ChatService] ✅ STOMP connected for userId=', userId);
      this.connectionSubject.next(true);

      this.stompClient?.subscribe(
        `/user/${userId}/queue/messages`,
        (msg: IMessage) => {
          try {
            const body: ChatMessage = JSON.parse(msg.body);
            console.log('[ChatService] 📩 Incoming message for userId=', userId, body);
            this.messagesSubject.next(body);
          } catch (err) {
            console.error('[ChatService] ❌ Failed to parse incoming message', err, msg.body);
          }
        }
      );
    };

    this.stompClient.onDisconnect = () => {
      console.log('[ChatService] ⚠️ STOMP disconnected for userId=', userId);
      this.connectionSubject.next(false);
    };

    this.stompClient.onStompError = (frame) => {
      console.error('[ChatService] ❌ STOMP error:', frame.headers['message']);
      console.error('[ChatService] Frame details:', frame.body);
    };

    this.stompClient.activate();
  }

  /**
   * Disconnect WebSocket
   */
  disconnectWebSocket(): void {
    if (this.stompClient) {
      console.log('[ChatService] 🔌 Disconnecting WebSocket…');
      this.stompClient.deactivate();
      this.connectionSubject.next(false);
    } else {
      console.log('[ChatService] ⚠️ No active WebSocket client to disconnect');
    }
  }

  /**
   * Send message via STOMP
   */
  sendMessage(senderId: number, receiverId: number, message: string): void {
    if (!this.stompClient || !this.stompClient.connected) {
      console.warn('[ChatService] ⚠️ STOMP client not connected. Message not sent.');
      return;
    }

    const msg: ChatMessage = {
      senderId,
      receiverId,
      message,
      timestamp: new Date().toISOString(),
      status: 'SENT',
    };

    console.log('[ChatService] ✉️ Sending message', msg);
    this.stompClient.publish({
      destination: '/app/chat.send',
      body: JSON.stringify(msg),
    });
  }

  /**
   * Compress image before upload
   */
  private async compressFile(file: File): Promise<File> {
    const options = {
      maxSizeMB: 5,
      maxWidthOrHeight: 1920,
      useWebWorker: true,
    };

    if (file.type.startsWith('image/')) {
      console.log('[ChatService] 🖼 Compressing image before upload:', file.name);
      return await imageCompression(file, options);
    }
    console.log('[ChatService] 📎 Skipping compression for non-image file:', file.name);
    return file;
  }

  /**
   * Upload file and return an Observable
   */
  uploadFile(senderId: number, receiverId: number, file: File): Observable<ChatMessage> {
    console.log('[ChatService] 📤 Uploading file for senderId=', senderId, 'receiverId=', receiverId, 'file=', file.name);
    return new Observable<ChatMessage>((observer) => {
      this.compressFile(file)
        .then((compressedFile) => {
          const formData = new FormData();
          formData.append('senderId', String(senderId));
          formData.append('receiverId', String(receiverId));
          formData.append('file', compressedFile, compressedFile.name);

          const req = new HttpRequest('POST', '/api/chats/upload', formData, {
            reportProgress: true,
          });

          this.http
            .request<ChatMessage>(req)
            .pipe(
              map((event) => {
                if (event.type === HttpEventType.Response) {
                  console.log('[ChatService] ✅ File upload completed:', event.body);
                  return event.body as ChatMessage;
                }
                return null as any;
              }),
              filter((msg) => msg != null)
            )
            .subscribe({
              next: (msg) => observer.next(msg),
              error: (err) => {
                console.error('[ChatService] ❌ File upload failed', err);
                observer.error(err);
              },
              complete: () => observer.complete(),
            });
        })
        .catch((err) => {
          console.error('[ChatService] ❌ File compression failed', err);
          observer.error(err);
        });
    });
  }

  /**
   * REST API: Get chat history with a contact
   */
  getChatHistory(contactId: number): Observable<ChatMessage[]> {
    console.log('[ChatService] 📜 Fetching chat history with contactId=', contactId);
    return this.http.get<ChatMessage[]>(`/api/chats/${contactId}`);
  }

  /**
   * REST API: Update message status (DELIVERED/READ)
   */
  updateStatus(messageId: number, status: 'READ' | 'DELIVERED'): Observable<any> {
    console.log('[ChatService] 🔄 Updating message status:', {messageId, status});
    return this.http.patch(`/api/chats/status/${messageId}`, {status});
  }

  /**
   * REST API: Get recent chats (WhatsApp-style list)
   */
  getRecentChats(): Observable<RecentChat[]> {
    console.log('[ChatService] 📜 Fetching recent chats');
    return this.http.get<RecentChat[]>(`/api/chats/recent`);
  }

  /**
   * REST API: Delete message (soft delete)
   */
  deleteMessage(messageId: number): Observable<any> {
    console.log('[ChatService] 🗑 Deleting messageId=', messageId);
    return this.http.delete(`/api/chats/${messageId}`);
  }
}
