/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest,} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from '@angular/router';
import {BehaviorSubject, Observable, throwError} from 'rxjs';
import {catchError, filter, switchMap, take} from 'rxjs/operators';
import {AuthService} from '../../auth/auth.service';

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

/**
 * ==========================================================
 * ✅ AuthInterceptor
 * ----------------------------------------------------------
 * • Automatically attaches JWT to all API requests
 * • Skips login/register/refresh endpoints
 * • Handles 401 refresh token flow gracefully
 * ==========================================================
 */
export const authInterceptorFn: HttpInterceptorFn = (
  req: HttpRequest<any>,
  next: HttpHandlerFn
): Observable<HttpEvent<any>> => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const jwt = authService.getToken();
  const normalizedUrl = req.url.replace(/\/+$/, '');

  // 🔍 Explicitly skip only login/register/refresh
  const skipAuthRoutes = [
    '/api/auth/login',
    '/api/auth/register',
    '/api/auth/refresh',
  ];
  const skipAuth = skipAuthRoutes.some((r) => normalizedUrl.endsWith(r));

  console.log('[AuthInterceptor] 🧭 Intercepting:', normalizedUrl);
  console.log('[AuthInterceptor] 🧩 skipAuth:', skipAuth, '| JWT present:', !!jwt);

  // 🛡️ Attach JWT unless it's a public route
  let authReq = req;
  if (!skipAuth && jwt) {
    authReq = req.clone({
      setHeaders: {Authorization: `Bearer ${jwt}`},
    });
    console.log('[AuthInterceptor] 🔐 Token attached for:', normalizedUrl);
  } else if (!skipAuth && !jwt) {
    console.warn('[AuthInterceptor] ⚠️ Missing JWT for:', normalizedUrl);
  } else {
    console.log('[AuthInterceptor] 🚫 Skipped token for public route:', normalizedUrl);
  }

  // ==========================================================
  // 🧩 Handle responses and refresh token if 401 Unauthorized
  // ==========================================================
  return next(authReq).pipe(
    catchError((err: any) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && !skipAuth) {
        console.warn('[AuthInterceptor] ⚠️ 401 → Attempting refresh flow');

        if (!isRefreshing) {
          isRefreshing = true;
          refreshTokenSubject.next(null);

          console.log('[AuthInterceptor] 🔄 Refreshing access token...');

          return authService.refreshToken().pipe(
            switchMap((res: any) => {
              const newToken = res?.auth_token || res?.token;
              if (newToken) {
                console.log('[AuthInterceptor] ✅ Token refreshed successfully');
                authService.setToken(newToken);
                refreshTokenSubject.next(newToken);
                isRefreshing = false;

                const retryReq = authReq.clone({
                  setHeaders: {Authorization: `Bearer ${newToken}`},
                });
                console.log('[AuthInterceptor] 🔁 Retrying original request:', normalizedUrl);
                return next(retryReq);
              } else {
                console.error('[AuthInterceptor] ❌ Refresh response missing token');
                isRefreshing = false;
                authService.logout();
                router.navigate(['/login']);
                return throwError(() => err);
              }
            }),
            catchError((refreshErr) => {
              console.error('[AuthInterceptor] ❌ Token refresh failed:', refreshErr);
              isRefreshing = false;
              authService.logout();
              router.navigate(['/login']);
              return throwError(() => refreshErr);
            })
          );
        } else {
          // Wait for refresh to complete
          return refreshTokenSubject.pipe(
            filter((token) => token != null),
            take(1),
            switchMap((token) => {
              console.log('[AuthInterceptor] ⏳ Queued request resuming with new token');
              const retryReq = authReq.clone({
                setHeaders: {Authorization: `Bearer ${token}`},
              });
              return next(retryReq);
            })
          );
        }
      }

      // 🚫 Pass all other errors to caller
      return throwError(() => err);
    })
  );
};
