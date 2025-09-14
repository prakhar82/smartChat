import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpErrorResponse, HttpClientModule } from '@angular/common/http';
import { trigger, transition, style, animate } from '@angular/animations';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
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
      const res: any = await this.http.post('/api/auth/login', {
        mobileNumber: this.mobileNumber,
        password: this.password
      }).toPromise();

      if (res?.accessToken && res?.userId) {
        localStorage.setItem('accessToken', res.accessToken);
        localStorage.setItem('refreshToken', res.refreshToken || '');
        localStorage.setItem('userId', String(res.userId));

        // Navigate to chats page after login
        this.router.navigate(['/chats']);
      } else {
        alert('Login failed: Invalid response from server');
      }
    } catch (err) {
      const error = err as HttpErrorResponse;
      const msg = error.error?.message || error.message || 'Unknown error';
      alert('Login failed: ' + msg);
      console.error('Login error:', error);
    }
  }
}
