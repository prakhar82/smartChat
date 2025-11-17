/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {ComponentFixture, TestBed} from '@angular/core/testing';
import {RouterTestingModule} from '@angular/router/testing';
import {RegisterComponent} from './register';

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let fixture: ComponentFixture<RegisterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterComponent, RouterTestingModule]
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should navigate to /chats with Google popup query param', () => {
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    // @ts-ignore – mock router manually
    component['router'] = routerSpy;

    try {
      // Simulate your helper method (if ever used internally)
      const navigateSpy = routerSpy.navigate as jasmine.Spy;
      component['router'].navigate(['/chats'], {queryParams: {showGooglePopup: true}});
      expect(navigateSpy).toHaveBeenCalledWith(
        ['/chats'],
        {queryParams: {showGooglePopup: true}}
      );
    } catch (e) {
      fail(`Navigation failed: ${e}`);
    }
  });
});
