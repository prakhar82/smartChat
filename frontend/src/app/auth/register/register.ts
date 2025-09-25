/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

import { Component } from '@angular/core';

@Component({
  selector: 'app-register',
  imports: [],
  templateUrl: './register.html',
  styleUrl: './register.scss'
})
export class Register {



// helper: navigate to chats and prompt google popup
protected openChatsAndPromptGoogle() {
  try {
    // @ts-ignore
    if (this.router) this.router.navigate(['/chats'], { queryParams: { showGooglePopup: true } });
  } catch (e) { console.error(e); }
}
}
