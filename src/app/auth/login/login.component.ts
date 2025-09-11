import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
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
