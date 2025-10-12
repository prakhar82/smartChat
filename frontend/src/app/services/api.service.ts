/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {catchError} from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private baseUrl = '/api'; // proxied in nginx/docker

  constructor(private http: HttpClient) {
  }

  get<T>(url: string, fallback: T) {
    return this.http.get<T>(`${this.baseUrl}${url}`).pipe(
      catchError(() => {
        console.warn(`API GET ${url} failed, using fallback`);
        return of(fallback);
      })
    );
  }

  post<T>(url: string, body: any, fallback: T) {
    return this.http.post<T>(`${this.baseUrl}${url}`, body).pipe(
      catchError(() => {
        console.warn(`API POST ${url} failed, using fallback`);
        return of(fallback);
      })
    );
  }
}
