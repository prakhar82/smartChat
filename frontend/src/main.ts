/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

import {bootstrapApplication} from '@angular/platform-browser';
import {provideAnimationsAsync} from '@angular/platform-browser/animations/async';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {provideRouter} from '@angular/router';

import {AppComponent} from './app/app.component';
import {appConfig} from './app/app.config';
import {routes} from './app/app.routes';
import {authInterceptorFn} from '../src/app/core/interceptors/auth.interceptor-fn';

bootstrapApplication(AppComponent, {
  ...appConfig,
  providers: [
    ...appConfig.providers,
    provideAnimationsAsync(),
    provideHttpClient(
      withInterceptors([authInterceptorFn]) // ✅ register the interceptor here
    ),
    provideRouter(routes),
  ],
}).catch((err) => console.error(err));
