/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Pipe, PipeTransform} from '@angular/core';
import {MatchedContact} from '../../contacts/contact.service';

@Pipe({
  name: 'contactFilter',
  standalone: true, // <-- IMPORTANT for standalone component
})
export class ContactFilterPipe implements PipeTransform {
  transform(contacts: MatchedContact[], query: string): MatchedContact[] {
    if (!contacts) return [];
    if (!query || query.trim() === '') return contacts;

    const lowerQuery = query.toLowerCase();
    return contacts.filter(
      (c) =>
        c.contactName.toLowerCase().includes(lowerQuery) ||
        c.phones.some((p) => p.value.includes(lowerQuery))
    );
  }
}
