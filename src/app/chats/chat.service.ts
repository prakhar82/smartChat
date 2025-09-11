import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private baseUrl = '/api';

  constructor(private http: HttpClient) {}

  getMatchedContacts(userId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/contacts/matched?userId=${userId}`);
  }

    getChatHistory(contactId: number, userId: number | null): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/chats/${contactId}?userId=${userId}`);
  }

  sendMessage(senderId: number | null, receiverId: number, message: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/chats/send`, { senderId, receiverId, message });
  }
}
