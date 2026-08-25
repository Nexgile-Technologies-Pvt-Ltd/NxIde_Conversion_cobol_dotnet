import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import {
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
  withRouterConfig,
} from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
      // Selecting the sidebar entry for the screen already on display must still count as a
      // navigation. Without this the router discards it, so a list left holding a filter that
      // matches nothing stays empty no matter how often its own menu entry is chosen.
      withRouterConfig({ onSameUrlNavigation: 'reload' }),
    ),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
