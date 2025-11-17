/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {ChangeDetectionStrategy, Component} from '@angular/core';
import {CommonModule} from '@angular/common';

/* =========================================================
 * 🧭 PresenceLegendComponent
 * ---------------------------------------------------------
 * A small, reusable legend explaining the meaning of
 * presence indicator colors (Online / Busy / Away / Offline).
 * Typically used in sidebar or contact list footer.
 * ========================================================= */
@Component({
  selector: 'app-presence-legend',
  standalone: true,
  templateUrl: './presence-legend.component.html',
  styleUrls: ['./presence-legend.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule
  ]
})
export class PresenceLegendComponent {
  /** 🔹 Legend items with status label + CSS class */
  legendItems = [
    {label: 'Online', class: 'online'},
    {label: 'Away', class: 'away'},
    {label: 'Busy', class: 'busy'},
    {label: 'Offline', class: 'offline'},
  ];
}
