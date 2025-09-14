import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SessionService } from './session.service';
import { SessionWarningComponent } from './session-warning/session-warning.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SessionWarningComponent],
  template: `
    <router-outlet></router-outlet>
    <app-session-warning
      [visible]="showWarning"
      [countdown]="countdown"
    ></app-session-warning>
  `
})
export class AppComponent implements OnInit {
  showWarning = false;
  countdown: number | null = null;

  constructor(private session: SessionService) {}

  ngOnInit() {
    const token = localStorage.getItem('accessToken');
    if (token) {
      this.session.startSessionFromToken(token);
    }

    this.session.countdown$.subscribe((sec) => {
      this.showWarning = sec >= 0;
      this.countdown = sec;
    });
  }
}
