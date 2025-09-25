/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Register } from './register';

describe('Register', () => {
  let component: Register;
  let fixture: ComponentFixture<Register>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });


// helper: navigate to chats and prompt google popup
protected openChatsAndPromptGoogle() {
  try {
    // @ts-ignore
    if (this.router) this.router.navigate(['/chats'], { queryParams: { showGooglePopup: true } });
  } catch (e) { console.error(e); }
}
});
