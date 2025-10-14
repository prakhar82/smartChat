/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Pipe, PipeTransform} from '@angular/core';

/**
 * 🕒 LastSeenPipe — WhatsApp-style "last seen" formatter
 */
@Pipe({
  name: 'lastSeen',
  standalone: true,
})
export class LastSeenPipe implements PipeTransform {
  transform(value: Date | string | null | undefined): string {
    if (!value) return 'a while ago';

    const date = value instanceof Date ? value : new Date(value);
    const now = new Date();

    const diffSec = (now.getTime() - date.getTime()) / 1000;
    const diffMin = diffSec / 60;
    const diffHr = diffMin / 60;
    const diffDays = diffHr / 24;

    // 🟢 <30s → Just now
    if (diffSec < 30) return 'Just now';

    // 🟢 <1h → n min ago
    if (diffMin < 60) return `${Math.floor(diffMin)} min ago`;

    // 🟢 <24h → n h ago
    if (diffHr < 24) return `${Math.floor(diffHr)} h ago`;

    // 🟡 Yesterday
    const yesterday = new Date();
    yesterday.setDate(now.getDate() - 1);
    if (
      date.getDate() === yesterday.getDate() &&
      date.getMonth() === yesterday.getMonth() &&
      date.getFullYear() === yesterday.getFullYear()
    ) {
      return `Yesterday, ${date.toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
      })}`;
    }

    // 🟡 Within a week → weekday, time
    if (diffDays < 7) {
      const weekday = date.toLocaleDateString([], {weekday: 'short'});
      const time = date.toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
      });
      return `${weekday}, ${time}`;
    }

    // 🟣 Older → Month Day, time
    const monthDay = date.toLocaleDateString([], {
      month: 'short',
      day: 'numeric',
    });
    const time = date.toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
    });
    return `${monthDay}, ${time}`;
  }
}
