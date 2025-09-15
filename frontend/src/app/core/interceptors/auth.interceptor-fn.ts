/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

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

  let authReq = req;
  if (token && !req.url.includes('/api/auth/')) {
    authReq = req.clone({setHeaders: {Authorization: `Bearer ${token}`}});
  }

  return next(authReq).pipe(
    catchError((err: any) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        return handle401Error(authReq, next, authService, router);
      }
      return throwError(() => err);
    })
  );
};

function handle401Error(
  request: HttpRequest<any>,
  next: HttpHandlerFn,
  authService: AuthService,
  router: Router
): Observable<HttpEvent<any>> {
  if (!isRefreshing) {
    isRefreshing = true;
    refreshTokenSubject.next(null);

    return authService.refreshToken().pipe(
      switchMap(res => {
        isRefreshing = false;
        refreshTokenSubject.next(res.accessToken);
        return next(
          request.clone({setHeaders: {Authorization: `Bearer ${res.accessToken}`}})
        );
      }),
      catchError(err => {
        isRefreshing = false;
        authService.logout();
        router.navigate(['/login']);
        return throwError(() => err);
      })
    );
  } else {
    return refreshTokenSubject.pipe(
      filter(token => token != null),
      take(1),
      switchMap(token =>
        next(request.clone({setHeaders: {Authorization: `Bearer ${token}`}}))
      )
    );
  }
}
