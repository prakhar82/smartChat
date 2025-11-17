/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest,} from '@angular/common/http';
import {inject} from '@angular/core';
import {BehaviorSubject, Observable, throwError} from 'rxjs';
import {catchError, filter, switchMap, take} from 'rxjs/operators';
import {AuthService} from '../../features/auth/auth.service';
import {environment} from '../../../environments/environment.prod';

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

/**
 * ==========================================================
 * 🔐 AuthInterceptorFn (Standalone Functional Interceptor)
 * ----------------------------------------------------------
 * - Attaches JWT to outgoing requests
 * - Skips login/register/refresh calls
 * - Handles automatic token refresh on 401
 * - Redirects to login on network/auth failure
 * ==========================================================
 */
export const authInterceptorFn: HttpInterceptorFn = (
  req: HttpRequest<any>,
  next: HttpHandlerFn
): Observable<HttpEvent<any>> => {
  const authService = inject(AuthService);
  const jwt = authService.getToken();

  // 🧩 Normalize URL (strip trailing slashes)
  const normalizedUrl = req.url.replace(/\/+$/, '');
  const apiUrl = (() => {
    try {
      return new URL(normalizedUrl, window.location.origin);
    } catch {
      // fallback for relative URLs
      return {pathname: normalizedUrl};
    }
  })();

  // ==========================================================
  // 🛑 Routes that should NOT include the Authorization header
  // ==========================================================
  const skipAuthRoutes = [
    '/auth/login',
    '/auth/register',
    '/auth/refresh',

    // Google OAuth (contact service)
    '/contact/google/init',
    '/contact/google/callback'

    // Google public endpoints do NOT require Authorization
    //'/contact/google/token'
  ];

  const skipAuth = skipAuthRoutes.some((r) =>
    apiUrl.pathname.toLowerCase().includes(r)
  );

  // ==========================================================
  // ✅ Clone request and attach token if needed
  // ==========================================================
  let authReq = req;
  if (!skipAuth && jwt) {
    authReq = req.clone({
      setHeaders: {Authorization: `Bearer ${jwt}`},
    });
  }

  // 🔍 Debug log (safe for dev only)
  if (!environment.production) {
    console.debug('[AuthInterceptor]', {
      url: req.url,
      skipAuth,
      tokenAdded: !!jwt && !skipAuth,
      authHeader: authReq.headers.get('Authorization'),
    });
  }

  // ==========================================================
  // 🧠 Main handler with refresh logic
  // ==========================================================
  return next(authReq).pipe(
    catchError((err: any) => {
      // 🔄 Handle Unauthorized (401)
      if (err instanceof HttpErrorResponse && err.status === 401 && !skipAuth) {
        console.warn('[AuthInterceptor] ⚠️ 401 — attempting token refresh');

        if (!isRefreshing) {
          isRefreshing = true;
          refreshTokenSubject.next(null);

          return authService.refreshToken().pipe(
            switchMap((res: any) => {
              const newToken = res?.auth_token || res?.token;
              if (newToken) {
                authService.setToken(newToken);
                refreshTokenSubject.next(newToken);
                isRefreshing = false;

                const retryReq = authReq.clone({
                  setHeaders: {Authorization: `Bearer ${newToken}`},
                });
                console.info(
                  '[AuthInterceptor] ✅ Token refreshed — retrying request'
                );
                return next(retryReq);
              } else {
                console.error('[AuthInterceptor] ❌ Refresh returned no token');
                isRefreshing = false;
                authService.logout();
                return throwError(() => err);
              }
            }),
            catchError((refreshErr) => {
              console.error(
                '[AuthInterceptor] ❌ Token refresh failed:',
                refreshErr
              );
              isRefreshing = false;
              authService.logout();
              return throwError(() => refreshErr);
            })
          );
        } else {
          // 🔁 Wait for refresh to complete
          return refreshTokenSubject.pipe(
            filter((token) => token != null),
            take(1),
            switchMap((token) => {
              const retryReq = authReq.clone({
                setHeaders: {Authorization: `Bearer ${token}`},
              });
              return next(retryReq);
            })
          );
        }
      }

      // ⚙️ Handle generic network errors (CORS, SSL, gateway issues)
      if (err instanceof HttpErrorResponse && err.status === 0) {
        console.error('[AuthInterceptor] ❌ Network or CORS error:', err);
        // Don’t immediately log out — allow user to retry
      }

      return throwError(() => err);
    })
  );
};
