/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

// src/app/core/interceptors/auth.interceptor.ts
import {HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from '@angular/router';
import {AuthService} from '../../auth/auth.service';
import {BehaviorSubject, Observable, throwError} from 'rxjs';
import {catchError, filter, switchMap, take} from 'rxjs/operators';

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

export const authInterceptorFn: HttpInterceptorFn = (
  req: HttpRequest<any>,
  next: HttpHandlerFn
): Observable<HttpEvent<any>> => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const token = authService.getToken();

  // Skip attaching token to auth endpoints
  const skipAuth = req.url.includes('/api/auth/');
  let authReq = req;

  if (token && !skipAuth) {
    authReq = req.clone({setHeaders: {Authorization: `Bearer ${token}`}});
    console.log('🔍 Interceptor attached token for:', req.url);
  } else {
    console.log('🔍 Interceptor skipped token for:', req.url);
  }

  return next(authReq).pipe(
    catchError((err: any) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        // handle refresh token sequence
        if (!isRefreshing) {
          isRefreshing = true;
          refreshTokenSubject.next(null);

          return authService.refreshToken().pipe(
            switchMap((res: any) => {
              isRefreshing = false;
              const newToken = res?.accessToken;
              if (newToken) {
                authService.setToken(newToken);
                refreshTokenSubject.next(newToken);
                // retry original request with new token
                return next(req.clone({setHeaders: {Authorization: `Bearer ${newToken}`}}));
              } else {
                // no token -> force logout
                authService.logout();
                router.navigate(['/login']);
                return throwError(() => err);
              }
            }),
            catchError((refreshErr) => {
              isRefreshing = false;
              authService.logout();
              router.navigate(['/login']);
              return throwError(() => refreshErr);
            })
          );
        } else {
          // queue other requests while refresh is in progress
          return refreshTokenSubject.pipe(
            filter(t => t != null),
            take(1),
            switchMap((t) => next(req.clone({setHeaders: {Authorization: `Bearer ${t}`}})))
          );
        }
      }
      return throwError(() => err);
    })
  );
};
