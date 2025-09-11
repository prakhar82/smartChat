import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
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
