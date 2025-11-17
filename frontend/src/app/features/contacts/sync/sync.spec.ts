/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Sync } from './sync';

describe('Sync', () => {
  let component: Sync;
  let fixture: ComponentFixture<Sync>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Sync]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Sync);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
