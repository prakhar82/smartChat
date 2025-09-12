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
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    HttpClientModule // ✅ needed for HttpClient
  ],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css'],
  animations: [
    trigger('slideInLeft', [
      transition(':enter', [
        style({ transform: 'translateX(-100%)', opacity: 0 }),
        animate(
          '600ms ease-out',
          style({ transform: 'translateX(0)', opacity: 1 })
        )
      ])
    ])
  ]
})
export class RegisterComponent {
  mobileNumber = '';
  password = '';

  constructor(private http: HttpClient, private router: Router) {}

  async register() {
    try {
      const res: any = await this.http
        .post('/api/auth/register', {
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
      console.error('Register failed', error);
      const errorMsg =
        error.error?.message || error.message || 'Unknown error occurred';
      alert('Register failed: ' + errorMsg);
    }
  }
}
