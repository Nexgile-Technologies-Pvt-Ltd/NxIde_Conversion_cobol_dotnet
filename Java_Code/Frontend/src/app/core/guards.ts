import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Protects every screen behind the sign-on, exactly as the CICS estate reached each transaction
 * only after COSGN00C had authenticated the terminal user.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/**
 * Administrator-only screens. The backend enforces the same rule on every endpoint; this guard
 * only avoids showing a screen that would fail.
 */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  if (auth.isAdmin()) {
    return true;
  }
  return router.createUrlTree(['/main-menu'], { queryParams: { denied: '1' } });
};

/**
 * Keeps a signed-on user away from the sign-on and sign-up screens.
 *
 * <p>A sign-off in flight still holds a session, because it is cleared only once the sign-on
 * screen has been reached. It is let through on that basis; turning it back here would strand
 * the sign-off on the screen it was leaving.</p>
 */
export const anonymousGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated() || auth.isSigningOff()) {
    return true;
  }
  return router.createUrlTree(['/dashboard']);
};
