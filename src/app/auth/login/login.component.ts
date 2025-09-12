import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpErrorResponse, HttpClientModule } from '@angular/common/http';
import {
  trigger,
  transition,
  style,
  animate
} from '@angular/animations';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    HttpClientModule // ✅ required for HttpClient to work
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('600ms ease-in', style({ opacity: 1 }))
      ])
    ])
  ]
})
export class LoginComponent {
  mobileNumber = '';
  password = '';

  constructor(private http: HttpClient, private router: Router) {}

  async login() {
    try {
      const res: any = await this.http
        .post('/api/auth/login', {
          mobileNumber: this.mobileNumber,
          password: this.password,
        })
        .toPromise();

      if (res?.accessToken) {
        localStorage.setItem('accessToken', res.accessToken);
        localStorage.setItem('refreshToken', res.refreshToken || '');
        localStorage.setItem('userId', String(res.userId || ''));
        this.router.navigate(['/chats']);
      }
    } catch (err) {
      const error = err as HttpErrorResponse;
      console.error('Login failed', error);
      const errorMsg =
        error.error?.message || error.message || 'Unknown error occurred';
      alert('Login failed: ' + errorMsg);
    }
  }
}
