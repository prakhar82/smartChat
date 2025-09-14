import { Injectable } from '@angular/core';
import {HttpClient, HttpEventType, HttpRequest} from '@angular/common/http';
import {BehaviorSubject, map, Observable} from 'rxjs';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import {filter} from 'rxjs/operators';
import imageCompression from 'browser-image-compression';

export interface ChatMessage {
  id?: number;
  senderId: number;
  receiverId: number;
  message?: string;
  fileUrl?: string;
  fileName?: string;
  emoji?: string;
  timestamp: string;
  status?: 'SENT' | 'DELIVERED' | 'READ';
}

@Injectable({
  providedIn: 'root',
})
export class ChatService {
  private stompClient?: Client;
  private messagesSubject = new BehaviorSubject<ChatMessage | null>(null);
  messages$ = this.messagesSubject.asObservable();

  constructor(private http: HttpClient) {}

  /**
   * Establish WebSocket (STOMP over SockJS) connection
   */
  connectWebSocket(userId: number): void {
    if (this.stompClient?.connected) return;

    const socket = new SockJS('/app'); // ✅ RabbitMQ STOMP endpoint

    this.stompClient = new Client({
      webSocketFactory: () => socket as any,
      reconnectDelay: 5000,
    });

    this.stompClient.onConnect = () => {
      console.log('✅ STOMP connected');

      // Subscribe to user-specific queue
      this.stompClient?.subscribe(`/user/${userId}/queue/messages`, (msg: IMessage) => {
        const body: ChatMessage = JSON.parse(msg.body);
        this.messagesSubject.next(body);
      });
    };

    this.stompClient.onStompError = (frame) => {
      console.error('❌ STOMP error:', frame.headers['message']);
    };

    this.stompClient.activate();
  }

  /**
   * Send message via STOMP
   */
  sendMessage(senderId: number, receiverId: number, message: string): void {
    if (!this.stompClient || !this.stompClient.connected) {
      console.warn('⚠️ STOMP client not connected');
      return;
    }

    const msg: ChatMessage = {
      senderId,
      receiverId,
      message,
      timestamp: new Date().toISOString(),
      status: 'SENT',
    };

    // ✅ Spring listens on @MessageMapping("/chat.send")
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
      return await imageCompression(file, options);
    }
    return file; // Non-image files are not compressed
  }

  /**
   * Upload file and return an Observable
   */
  uploadFile(senderId: number, receiverId: number, file: File): Observable<ChatMessage> {
    return new Observable<ChatMessage>(observer => {
      this.compressFile(file).then(compressedFile => {
        const formData = new FormData();
        formData.append('senderId', String(senderId));
        formData.append('receiverId', String(receiverId));
        formData.append('file', compressedFile, compressedFile.name);

        const req = new HttpRequest('POST', '/api/chat/upload', formData, {
          reportProgress: true,
        });

        this.http.request<ChatMessage>(req).pipe(
          map(event => {
            if (event.type === HttpEventType.Response) {
              return event.body as ChatMessage;
            }
            return null as any;
          }),
          filter(msg => msg != null)
        ).subscribe({
          next: msg => observer.next(msg),
          error: err => observer.error(err),
          complete: () => observer.complete()
        });
      }).catch(err => observer.error(err));
    });
  }

  /**
   * REST API: Get chat history
   */
  getChatHistory(contactId: number, userId: number): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`/api/chats/${contactId}?userId=${userId}`);
  }

  /**
   * REST API: Update message status (DELIVERED/READ)
   */
  updateStatus(messageId: number, status: 'READ' | 'DELIVERED'): Observable<any> {
    return this.http.patch(`/api/chats/status/${messageId}`, { status });
  }
}
