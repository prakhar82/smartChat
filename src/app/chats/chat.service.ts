import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject, Observable } from 'rxjs';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject } from 'rxjs';

export interface ChatMessage {
  id?: number;
  senderId: number;
  receiverId: number;
  message: string;
  emoji?: string | null;
  timestamp?: string;
  status?: 'SENT' | 'DELIVERED' | 'READ'; // ✅ added
}

@Injectable({
  providedIn: 'root',
})
export class ChatService {
  private stompClient?: Client;
  private messagesSubject = new Subject<ChatMessage>();
  messages$ = this.messagesSubject.asObservable();

  constructor(private http: HttpClient) {}

  connectWebSocket(userId: number): void {
    const socket = new SockJS('/ws-chat');

    this.stompClient = new Client({
      webSocketFactory: () => socket as any,
      reconnectDelay: 5000,
    });

    this.stompClient.onConnect = () => {
      this.stompClient?.subscribe(
        `/user/${userId}/topic/messages`,
        (msg: IMessage) => {
          if (msg?.body) {
            try {
              const body: ChatMessage = JSON.parse(msg.body);

              // ✅ immediately mark delivered if current user is receiver
              if (body.receiverId === userId && body.status === 'SENT') {
                this.updateStatus(body.id!, 'DELIVERED').subscribe();
              }

              // Default new incoming messages to "delivered"
              if (body && body.senderId && body.receiverId) {
                body.status = 'DELIVERED';
                this.messagesSubject.next(body);
              }
            } catch (e) {
              console.error('Invalid WS message', e);
            }
          }
        }
      );
    };

    this.stompClient.activate();
  }

  getMatchedContacts(userId: number): Observable<any[]> {
    return this.http.get<any[]>(`/api/contacts/matched?userId=${userId}`);
  }

  getChatHistory(contactId: number, userId: number): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`/api/chats/${contactId}?userId=${userId}`);
  }

  sendMessage(senderId: number, receiverId: number, message: string): Observable<ChatMessage> {
    const outgoing: ChatMessage = {
      senderId,
      receiverId,
      message,
      status: 'SENT', // ✅ mark immediately as sent
    };

    return this.http.post<ChatMessage>(`/api/chats/send`, outgoing);
  }

  updateStatus(messageId: number, status: 'DELIVERED' | 'READ'): Observable<ChatMessage> {
    return this.http.patch<ChatMessage>(`/api/chats/${messageId}/status?status=${status}`, {});
  }
}
