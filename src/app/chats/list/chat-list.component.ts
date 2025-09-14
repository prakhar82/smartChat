import { Component, Input,OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ContactService } from '../../contacts/contact.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-chat-list',
  imports: [CommonModule],
  templateUrl: './chat-list.component.html',
  styleUrls: ['./chat-list.component.css'],
  standalone: true
})
export class ChatListComponent implements OnInit {
  @Input() userId!: string;
  contacts: any[] = [];

  constructor(
    private contactService: ContactService,
    private auth: AuthService,
    private router: Router
  ) {}

  ngOnInit() {
    const userId = this.auth.getUserId();
    this.contactService.getMatchedContacts(userId).subscribe((res) => {
      this.contacts = res;
    });
  }

  openChat(contact: any) {
    this.router.navigate(['/chats', contact.matchedUserId]);
  }
}




























/*
import { Component, OnInit, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ContactService, MatchedContact } from '../../contacts/contact.service';

@Component({
  selector: 'app-chat-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-list.component.html',
  styleUrls: ['./chat-list.component.css']
})
export class ChatListComponent implements OnInit {
  @Input() userId!: number;

  contacts: MatchedContact[] = [];
  filteredContacts: MatchedContact[] = [];
  searchQuery = '';
  loading = false;
  error = '';

  constructor(private contactService: ContactService, private router: Router) {}

  ngOnInit(): void {
    if (!this.userId) {
      this.error = 'User not logged in';
      return;
    }
    this.loadContacts(this.userId);
  }

  private loadContacts(userId: number) {
    this.loading = true;
    this.contactService.getMatchedContacts(userId).subscribe({
      next: (data) => {
        this.contacts = data;
        this.filteredContacts = data;
        this.loading = false;
      },
      error: (e: any) => {
        console.error('Failed to load contacts', e);
        this.error = 'Failed to load contacts';
        this.loading = false;
      }
    });
  }

  filterContacts() {
    const q = this.searchQuery.toLowerCase().trim();
    this.filteredContacts = this.contacts.filter(c =>
      (c.contactName?.toLowerCase().includes(q)) ||
      (c.phoneNormalized?.includes(q))
    );
  }

  openChat(contact: MatchedContact) {
    const contactId = contact.matchedUserId || contact.contactId;
    if (!contactId) return;
    this.router.navigate(['/chats', contactId]);
  }

  sendInvite(event: Event, contact: MatchedContact) {
    event.stopPropagation();
    if (!contact.phoneNormalized || !contact.contactName) {
      this.error = 'Invalid contact info';
      return;
    }
    this.contactService.sendInviteEmail(contact.phoneNormalized, contact.contactName).subscribe({
      next: () => alert(`Invite sent to ${contact.contactName}`),
      error: (e: any) => {
        console.error('Error sending invite', e);
        this.error = 'Failed to send invite.';
      }
    });
  }
}
*/
