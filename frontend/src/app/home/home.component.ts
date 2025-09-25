/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

import {RouterModule} from '@angular/router';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Component} from '@angular/core';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterModule, CommonModule, FormsModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css'], // ✅ matches the file you created
})
export class HomeComponent {}
